package com.skillmirror.assessment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmirror.assessment.AssessmentDtos.*;
import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.entity.CandidateCv;
import com.skillmirror.candidate.entity.CvAnalysis;
import com.skillmirror.candidate.repository.CandidateCvRepository;
import com.skillmirror.candidate.repository.CvAnalysisRepository;
import com.skillmirror.candidate.service.CandidateAccess;
import com.skillmirror.common.Profile;
import com.skillmirror.recruiter.offer.SkillMatcher;
import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Évaluations par compétence.
 *  - 3 niveaux : le suivant est verrouillé tant que le précédent n'est pas réussi (>= 60 %) ;
 *  - 5 listes de questions par niveau (au moins 10 questions par liste) ;
 *  - tests proposés d'abord selon le profil (compétences du CV).
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    /** Score minimum (en %) pour réussir un niveau et débloquer le suivant. */
    private static final double PASS_SCORE = 60.0;
    private static final List<String> LEVELS = List.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Map<String, Integer> LEVEL_RANK =
            Map.of("BEGINNER", 1, "INTERMEDIATE", 2, "ADVANCED", 3);

    /** Si aucune liste n'est complète : taille de l'échantillon tiré dans la banque. */
    private static final int FALLBACK_SIZE = 10;
    /** Une liste très longue est limitée à ce nombre de questions. */
    private static final int MAX_LIST_SIZE = 20;

    private final CandidateAccess access;
    private final SkillRepository skills;
    private final QuestionRepository questions;
    private final AssessmentRepository assessments;
    private final QuestionGenerator generator;
    private final AssessmentStore store;
    private final CandidateCvRepository cvs;
    private final CvAnalysisRepository analyses;

    /** Mettre assessments.auto-fill=false (variable ASSESSMENTS_AUTO_FILL) pour couper le remplissage IA. */
    @Value("${assessments.auto-fill:true}")
    private boolean autoFill;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Random random = new Random();

    // ------------------------------------------------------------------
    // Tests proposés (selon le profil) avec niveaux verrouillés
    // ------------------------------------------------------------------

    public List<AvailableSkill> available(String uid) {
        Profile me = access.candidate(uid);

        Map<UUID, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : questions.countBySkillAndLevel()) {
            counts.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                    .put((String) row[1], (Long) row[2]);
        }
        Set<String> mine = profileSkills(me.getId());
        Map<String, Double> best = bestScores(assessments.findByProfileIdOrderByCreatedAtDesc(me.getId()));

        List<AvailableSkill> out = new ArrayList<>();
        for (Skill s : skills.findAllById(counts.keySet())) {
            Map<String, Long> perLevel = counts.get(s.getId());
            List<LevelStatus> levels = new ArrayList<>();
            for (String level : LEVELS) {
                int qc = perLevel.getOrDefault(level, 0L).intValue();
                Double b = best.get(s.getId() + ":" + level);
                levels.add(new LevelStatus(level, !isUnlocked(best, s.getId(), level),
                        qc > 0, b, b != null && b >= PASS_SCORE, qc));
            }
            out.add(new AvailableSkill(s.getId(), s.getName(), s.getCategory(),
                    mine.contains(SkillMatcher.normalize(s.getName())), levels));
        }
        out.sort(Comparator.comparing(AvailableSkill::recommended).reversed()
                .thenComparing(AvailableSkill::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ------------------------------------------------------------------
    // Démarrer un test
    // ------------------------------------------------------------------

    public StartResponse start(String uid, UUID skillId, String level, Integer ignored) {
        Profile me = access.candidate(uid);
        Skill skill = skill(skillId);
        List<Assessment> history = assessments.findByProfileIdOrderByCreatedAtDesc(me.getId());

        // Niveau verrouillé : vérifié côté serveur (on ne se fie pas à l'application)
        if (!isUnlocked(bestScores(history), skillId, level)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Niveau verrouillé : obtenez au moins " + (int) PASS_SCORE
                            + " % au niveau précédent pour le débloquer");
        }

        List<Question> all = new ArrayList<>(questions.findBySkillIdAndLevel(skillId, level));
        if (all.isEmpty()) {
            // Aucune question : on génère maintenant avec l'IA (le candidat attend)
            all.addAll(generator.generate(skill, level, 3).questions());
        }
        if (all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucune question disponible pour cette compétence à ce niveau");
        }

        Picked picked = pick(all, history, skillId, level);

        // Les listes incomplètes se remplissent en arrière-plan avec le RAG
        if (autoFill && !generator.isComplete(skillId, level)) {
            generator.fillAsync(skill, level);
        }

        Assessment a = new Assessment();
        a.setProfileId(me.getId());
        a.setSkillId(skillId);
        a.setLevel(level);
        a.setListNo(picked.listNo());
        a.setStatus("IN_PROGRESS");
        a.setTotalQuestions(picked.questions().size());
        a.setQuestionIds(picked.questions().stream().map(q -> q.getId().toString())
                .collect(Collectors.joining(",")));
        assessments.save(a);

        List<QuestionOut> out = picked.questions().stream()
                .map(q -> new QuestionOut(q.getId(), q.getQuestionText(), q.options()))
                .toList();
        return new StartResponse(a.getId(), skill.getName(), level, out);
    }

    private record Picked(List<Question> questions, Integer listNo) {}

    /**
     * Choisit les questions : une liste complète (au moins 10 questions), différente de la
     * dernière passée par le candidat si possible. Sans liste complète, un échantillon de la banque.
     */
    private Picked pick(List<Question> all, List<Assessment> history, UUID skillId, String level) {
        Map<Integer, List<Question>> byList = all.stream().collect(
                Collectors.groupingBy(q -> q.getListNo() == null ? 1 : q.getListNo()));

        List<Integer> complete = byList.entrySet().stream()
                .filter(e -> e.getValue().size() >= QuestionGenerator.MIN_LIST_SIZE)
                .map(Map.Entry::getKey).sorted().collect(Collectors.toList());

        List<Question> chosen;
        Integer listNo = null;
        if (!complete.isEmpty()) {
            Integer last = lastListNo(history, skillId, level);
            List<Integer> options = complete.stream()
                    .filter(n -> !Objects.equals(n, last)).collect(Collectors.toList());
            if (options.isEmpty()) options = complete;
            listNo = options.get(random.nextInt(options.size()));
            chosen = new ArrayList<>(byList.get(listNo));
            Collections.shuffle(chosen);
            if (chosen.size() > MAX_LIST_SIZE) chosen = new ArrayList<>(chosen.subList(0, MAX_LIST_SIZE));
        } else {
            chosen = new ArrayList<>(all);
            Collections.shuffle(chosen);
            if (chosen.size() > FALLBACK_SIZE) chosen = new ArrayList<>(chosen.subList(0, FALLBACK_SIZE));
        }
        return new Picked(chosen, listNo);
    }

    private static Integer lastListNo(List<Assessment> history, UUID skillId, String level) {
        for (Assessment a : history) {   // du plus récent au plus ancien
            if (skillId.equals(a.getSkillId()) && level.equals(a.getLevel()) && a.getListNo() != null) {
                return a.getListNo();
            }
        }
        return null;
    }

    /** Génération manuelle avec le RAG (utile pour tester / enrichir la banque). */
    public GenerateResponse generate(String uid, UUID skillId, String level, Integer requested) {
        access.candidate(uid);
        Skill skill = skill(skillId);
        QuestionGenerator.Result r = generator.generate(skill, level, requested == null ? 3 : requested);
        return new GenerateResponse(r.questions().size(), r.passagesUsed(),
                r.questions().stream().map(Question::getQuestionText).toList());
    }

    // ------------------------------------------------------------------
    // Correction automatique et calcul du résultat
    // ------------------------------------------------------------------

    public ResultResponse submit(String uid, UUID assessmentId, SubmitRequest req) {
        Profile me = access.candidate(uid);
        Assessment a = ownedAssessment(me, assessmentId);
        if (!"IN_PROGRESS".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce test est déjà terminé");
        }

        List<UUID> ids = Arrays.stream(a.getQuestionIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
        Map<UUID, Question> byId = new HashMap<>();
        questions.findAllById(ids).forEach(q -> byId.put(q.getId(), q));

        Map<UUID, Integer> given = new HashMap<>();
        for (AnswerIn x : req.answers()) given.put(x.questionId(), x.selectedIndex());

        List<ResultQuestion> details = new ArrayList<>();
        int correct = 0;
        for (UUID id : ids) {
            Question q = byId.get(id);
            if (q == null) continue;
            Integer selected = given.get(id);
            boolean ok = selected != null && selected.equals(q.getCorrectIndex());
            if (ok) correct++;
            details.add(new ResultQuestion(id, q.getQuestionText(), q.options(),
                    selected, q.getCorrectIndex(), ok, q.getExplanation()));
        }

        int total = details.size();
        double score = total == 0 ? 0 : Math.round(1000.0 * correct / total) / 10.0;
        boolean passed = score >= PASS_SCORE;
        String skillName = skills.findById(a.getSkillId()).map(Skill::getName).orElse("");

        List<String> strengths = passed ? List.of(skillName) : List.of();
        List<String> weaknesses = passed ? List.of() : List.of(skillName);
        store.saveResult(a.getId(), score, json(details), json(strengths), json(weaknesses));

        // Le niveau du candidat dans candidate_skills ne fait que monter
        if (passed && a.getSkillId() != null) {
            String current = store.currentLevel(me.getId(), a.getSkillId());
            if (rank(a.getLevel()) > rank(current)) {
                store.saveSkillLevel(me.getId(), a.getSkillId(), a.getLevel());
            }
        }
        return toResult(a, skillName, score, correct, total, Instant.now(), details);
    }

    public ResultResponse result(String uid, UUID assessmentId) {
        Profile me = access.candidate(uid);
        Assessment a = ownedAssessment(me, assessmentId);
        if (!"COMPLETED".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce test n'est pas terminé");
        }
        String stored = store.loadResults(a.getId()).orElse("[]");
        List<ResultQuestion> details;
        try {
            details = mapper.readValue(stored, new TypeReference<List<ResultQuestion>>() {});
        } catch (Exception e) {
            details = List.of();
        }
        int correct = (int) details.stream().filter(ResultQuestion::correct).count();
        double score = a.getScore() == null ? 0 : a.getScore();
        String skillName = a.getSkillId() == null ? ""
                : skills.findById(a.getSkillId()).map(Skill::getName).orElse("");
        return toResult(a, skillName, score, correct, details.size(), a.getFinishedAt(), details);
    }

    private ResultResponse toResult(Assessment a, String skillName, double score, int correct,
                                    int total, Instant finishedAt, List<ResultQuestion> details) {
        int idx = LEVELS.indexOf(a.getLevel());
        String next = (idx >= 0 && idx + 1 < LEVELS.size()) ? LEVELS.get(idx + 1) : null;
        return new ResultResponse(a.getId(), a.getSkillId(), skillName, a.getLevel(), score,
                correct, total, score >= PASS_SCORE, PASS_SCORE, next, "COMPLETED",
                finishedAt, details);
    }

    // ------------------------------------------------------------------
    // Historique
    // ------------------------------------------------------------------

    public List<HistoryItem> history(String uid) {
        Profile me = access.candidate(uid);
        List<Assessment> list = assessments.findByProfileIdOrderByCreatedAtDesc(me.getId());
        Map<UUID, String> names = new HashMap<>();
        skills.findAllById(list.stream().map(Assessment::getSkillId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(s -> names.put(s.getId(), s.getName()));

        return list.stream()
                .map(a -> new HistoryItem(a.getId(), a.getSkillId(),
                        names.getOrDefault(a.getSkillId(), "—"), a.getLevel(), a.getStatus(),
                        a.getScore(), a.getTotalQuestions(), a.getCreatedAt(), a.getFinishedAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** Meilleur score terminé par compétence et niveau : clé "skillId:LEVEL". */
    private static Map<String, Double> bestScores(List<Assessment> history) {
        Map<String, Double> best = new HashMap<>();
        for (Assessment a : history) {
            if (!"COMPLETED".equals(a.getStatus()) || a.getScore() == null || a.getSkillId() == null) continue;
            best.merge(a.getSkillId() + ":" + a.getLevel(), a.getScore(), Math::max);
        }
        return best;
    }

    /** Débutant toujours ouvert ; un niveau s'ouvre quand le précédent est réussi (>= 60 %). */
    private static boolean isUnlocked(Map<String, Double> best, UUID skillId, String level) {
        int idx = LEVELS.indexOf(level);
        if (idx < 0) return false;
        if (idx == 0) return true;
        Double previous = best.get(skillId + ":" + LEVELS.get(idx - 1));
        return previous != null && previous >= PASS_SCORE;
    }

    /** Compétences du CV du candidat (analyse extraite ou confirmée), sans accents ni casse. */
    private Set<String> profileSkills(UUID profileId) {
        Set<String> out = new HashSet<>();
        try {
            Optional<CandidateCv> cv = cvs.findFirstByProfileIdAndMainTrueOrderByUploadedAtDesc(profileId)
                    .or(() -> cvs.findFirstByProfileIdOrderByUploadedAtDesc(profileId));
            cv.flatMap(c -> analyses.findByCvId(c.getId()))
                    .filter(a -> "EXTRACTED".equals(a.getStatus()) || "CONFIRMED".equals(a.getStatus()))
                    .map(CvAnalysis::getData)
                    .ifPresent((CvAnalysisData d) -> {
                        d.technicalSkills().forEach(s -> out.add(SkillMatcher.normalize(s)));
                        d.softSkills().forEach(s -> out.add(SkillMatcher.normalize(s)));
                    });
        } catch (RuntimeException ignored) {
            // pas de CV exploitable : aucune recommandation, tous les tests restent accessibles
        }
        return out;
    }

    private Assessment ownedAssessment(Profile me, UUID id) {
        return assessments.findById(id)
                .filter(a -> me.getId().equals(a.getProfileId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test introuvable"));
    }

    private Skill skill(UUID id) {
        return skills.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Compétence introuvable"));
    }

    private static int rank(String level) {
        return level == null ? 0 : LEVEL_RANK.getOrDefault(level, 0);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON invalide", e);
        }
    }
}