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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parcours par niveau (principe Duolingo) :
 *  - un niveau = 5 listes à terminer dans l'ordre (1 test = 1 liste) ;
 *  - l'état est sauvegardé (liste courante, cumul, réponses en cours) ;
 *  - le niveau suivant se débloque seulement après la 5e liste ET si le score cumulé >= 60 %.
 *  - le RAG ne génère que ce qui manque dans la base.
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final double PASS_SCORE = 60.0;
    private static final int NB_LISTS = QuestionGenerator.LISTS_PER_LEVEL;
    private static final List<String> LEVELS = List.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Map<String, Integer> LEVEL_RANK =
            Map.of("BEGINNER", 1, "INTERMEDIATE", 2, "ADVANCED", 3);

    private final CandidateAccess access;
    private final SkillRepository skills;
    private final QuestionRepository questions;
    private final AssessmentRepository assessments;
    private final LevelProgressRepository progresses;
    private final QuestionGenerator generator;
    private final AssessmentStore store;
    private final CandidateCvRepository cvs;
    private final CvAnalysisRepository analyses;
    private final KnowledgeStore knowledge;

    @Value("${assessments.auto-fill:true}")
    private boolean autoFill;

    /** Taille minimale d'une liste (10 en production ; baissez-la temporairement pour une démo). */
    @Value("${assessments.min-list-size:10}")
    private int minListSize;

    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // Liste des tests avec état de chaque niveau
    // ------------------------------------------------------------------

    public List<AvailableSkill> available(String uid) {
        Profile me = access.candidate(uid);

        // UNE seule requête : nombre de questions par compétence et par niveau
        Map<UUID, Map<String, Long>> counts = new HashMap<>();
        for (Object[] row : questions.countBySkillAndLevel()) {
            counts.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                    .put((String) row[1], (Long) row[2]);
        }

        Set<String> mine = profileSkills(me.getId());
        Map<String, LevelProgress> prog = progressMap(me.getId());

        List<AvailableSkill> out = new ArrayList<>();
        for (Skill s : skills.findAll()) {
            boolean inProfile = mine.contains(SkillMatcher.normalize(s.getName()));
            boolean hasQuestions = counts.containsKey(s.getId());

            if (!hasQuestions) {
                if (!inProfile) continue;   // ni questions, ni dans le profil : on ignore
                // compétence du profil sans question : visible seulement si un cours existe pour le RAG
                if (knowledge.firstChunks(s.getId(), 1).isEmpty()) continue;
            }

            Map<String, Long> perLevel = counts.getOrDefault(s.getId(), Map.of());
            List<LevelStatus> levels = new ArrayList<>();
            for (String level : LEVELS) {
                LevelProgress p = prog.get(key(s.getId(), level));
                int qc = perLevel.getOrDefault(level, 0L).intValue();
                boolean passed = p != null && "PASSED".equals(p.getStatus());
                int done = p == null ? 0 : (passed || "FAILED".equals(p.getStatus())
                        ? NB_LISTS : p.getNextList() - 1);
                levels.add(new LevelStatus(level, !isUnlocked(prog, s.getId(), level), true,
                        p == null || p.getQuestionTotal() == 0 ? null : p.score(),
                        passed, qc, p == null ? "IN_PROGRESS" : p.getStatus(), done, NB_LISTS));
            }
            out.add(new AvailableSkill(s.getId(), s.getName(), s.getCategory(), inProfile, levels));
        }
        out.sort(Comparator.comparing(AvailableSkill::recommended).reversed()
                .thenComparing(AvailableSkill::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
    // ------------------------------------------------------------------
    // Démarrer ou reprendre la liste courante du niveau
    // ------------------------------------------------------------------

    @Transactional
    public StartResponse start(String uid, UUID skillId, String level, Integer ignored) {
        Profile me = access.candidate(uid);
        Skill skill = skill(skillId);
        Map<String, LevelProgress> prog = progressMap(me.getId());

        if (!isUnlocked(prog, skillId, level)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Niveau verrouillé : terminez les 5 listes du niveau précédent avec au moins "
                            + (int) PASS_SCORE + " % de score global");
        }
        LevelProgress p = progress(me.getId(), skillId, level);
        if ("PASSED".equals(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Niveau déjà validé");
        }
        if ("FAILED".equals(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Score global insuffisant : refaites le niveau ou allez au tableau de bord");
        }

        int listNo = p.getNextList();

        // 1) Reprise : un test de cette liste est déjà commencé
        Optional<Assessment> open = assessments
                .findByProfileIdOrderByCreatedAtDesc(me.getId()).stream()
                .filter(a -> "IN_PROGRESS".equals(a.getStatus())
                        && skillId.equals(a.getSkillId()) && level.equals(a.getLevel())
                        && Objects.equals(a.getListNo(), listNo))
                .findFirst();
        if (open.isPresent()) {
            Assessment a = open.get();
            return response(a, skill, p, orderedQuestions(a), readDraft(a));
        }

        // 2) Nouvelle liste : on utilise celle de la base si elle est assez remplie
        List<Question> levelQuestions = questions.findBySkillIdAndLevel(skillId, level);
        List<Question> list = levelQuestions.stream()
                .filter(q -> (q.getListNo() == null ? 1 : q.getListNo()) == listNo)
                .collect(Collectors.toCollection(ArrayList::new));

        if (list.size() < minListSize) {
            // Il manque des questions : le RAG intervient, et seulement pour ce qui manque
            if (autoFill) {
                if (levelQuestions.isEmpty()) {
                    generator.fillAsync(skill, level);                          // niveau vide : toutes les listes
                } else {
                    generator.fillListAsync(skill, level, listNo, minListSize); // seulement cette liste
                }
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La liste " + listNo + " est en cours de génération par l'IA ("
                            + list.size() + "/" + minListSize + "). Réessayez dans quelques minutes.");
        }
        Collections.shuffle(list);

        Assessment a = new Assessment();
        a.setProfileId(me.getId());
        a.setSkillId(skillId);
        a.setLevel(level);
        a.setListNo(listNo);
        a.setStatus("IN_PROGRESS");
        a.setTotalQuestions(list.size());
        a.setQuestionIds(list.stream().map(q -> q.getId().toString()).collect(Collectors.joining(",")));
        a.setCurrentIndex(0);
        assessments.save(a);
        return response(a, skill, p, list, Map.of());
    }

    private StartResponse response(Assessment a, Skill skill, LevelProgress p,
                                   List<Question> qs, Map<UUID, Integer> saved) {
        List<QuestionOut> out = qs.stream()
                .map(q -> new QuestionOut(q.getId(), q.getQuestionText(), q.options())).toList();
        return new StartResponse(a.getId(), skill.getName(), a.getLevel(), p.getNextList(),
                p.getNextList() - 1, NB_LISTS,
                a.getCurrentIndex() == null ? 0 : a.getCurrentIndex(), saved, out);
    }

    // ------------------------------------------------------------------
    // Sauvegarde en cours de liste (le candidat peut quitter et reprendre)
    // ------------------------------------------------------------------

    public void saveDraft(String uid, UUID assessmentId, SaveRequest req) {
        Profile me = access.candidate(uid);
        Assessment a = ownedAssessment(me, assessmentId);
        if (!"IN_PROGRESS".equals(a.getStatus())) return;
        a.setDraftAnswers(json(req.answers()));
        a.setCurrentIndex(req.index());
        assessments.save(a);
    }

    // ------------------------------------------------------------------
    // Terminer une liste : cumul, puis évaluation du niveau après la 5e
    // ------------------------------------------------------------------

    @Transactional
    public ResultResponse submit(String uid, UUID assessmentId, SubmitRequest req) {
        Profile me = access.candidate(uid);
        Assessment a = ownedAssessment(me, assessmentId);
        if (!"IN_PROGRESS".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce test est déjà terminé");
        }

        List<UUID> ids = idsOf(a);
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
        String skillName = skills.findById(a.getSkillId()).map(Skill::getName).orElse("");

        store.saveResult(a.getId(), score, json(details),
                json(score >= PASS_SCORE ? List.of(skillName) : List.of()),
                json(score >= PASS_SCORE ? List.of() : List.of(skillName)));

        // --- cumul sur le niveau ---
        LevelProgress p = progress(me.getId(), a.getSkillId(), a.getLevel());
        if ("IN_PROGRESS".equals(p.getStatus()) && Objects.equals(a.getListNo(), p.getNextList())) {
            p.setCorrectTotal(p.getCorrectTotal() + correct);
            p.setQuestionTotal(p.getQuestionTotal() + total);
            p.setNextList(p.getNextList() + 1);
            if (p.getNextList() > NB_LISTS) {                         // 5e liste terminée
                p.setStatus(p.score() >= PASS_SCORE ? "PASSED" : "FAILED");
                if ("PASSED".equals(p.getStatus())) raiseSkillLevel(me.getId(), a);
            }
            p.setUpdatedAt(Instant.now());
            progresses.save(p);
        }
        return toResult(a, skillName, score, correct, total, Instant.now(), details, p);
    }

    public ResultResponse result(String uid, UUID assessmentId) {
        Profile me = access.candidate(uid);
        Assessment a = ownedAssessment(me, assessmentId);
        if (!"COMPLETED".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce test n'est pas terminé");
        }
        List<ResultQuestion> details;
        try {
            details = mapper.readValue(store.loadResults(a.getId()).orElse("[]"),
                    new TypeReference<List<ResultQuestion>>() {});
        } catch (Exception e) {
            details = List.of();
        }
        int correct = (int) details.stream().filter(ResultQuestion::correct).count();
        String skillName = a.getSkillId() == null ? ""
                : skills.findById(a.getSkillId()).map(Skill::getName).orElse("");
        LevelProgress p = progress(me.getId(), a.getSkillId(), a.getLevel());
        return toResult(a, skillName, a.getScore() == null ? 0 : a.getScore(), correct,
                details.size(), a.getFinishedAt(), details, p);
    }

    private ResultResponse toResult(Assessment a, String skillName, double score, int correct,
                                    int total, Instant finishedAt, List<ResultQuestion> details,
                                    LevelProgress p) {
        boolean finished = !"IN_PROGRESS".equals(p.getStatus());
        boolean passed = "PASSED".equals(p.getStatus());
        int idx = LEVELS.indexOf(a.getLevel());
        String next = (passed && idx + 1 < LEVELS.size()) ? LEVELS.get(idx + 1) : null;
        int done = finished ? NB_LISTS : p.getNextList() - 1;
        return new ResultResponse(a.getId(), a.getSkillId(), skillName, a.getLevel(),
                a.getListNo() == null ? 0 : a.getListNo(), score, correct, total,
                done, NB_LISTS, finished, passed, p.score(), PASS_SCORE,
                next, "COMPLETED", finishedAt, details);
    }

    // ------------------------------------------------------------------
    // Refaire le niveau (seulement après un échec)
    // ------------------------------------------------------------------

    @Transactional
    public void restart(String uid, UUID skillId, String level) {
        Profile me = access.candidate(uid);
        LevelProgress p = progress(me.getId(), skillId, level);
        if (!"FAILED".equals(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le niveau n'est pas en échec");
        }
        p.setAttempt(p.getAttempt() + 1);
        p.setNextList(1);
        p.setCorrectTotal(0);
        p.setQuestionTotal(0);
        p.setStatus("IN_PROGRESS");
        p.setUpdatedAt(Instant.now());
        progresses.save(p);
    }

    // ------------------------------------------------------------------
    // Génération manuelle + historique
    // ------------------------------------------------------------------

    public GenerateResponse generate(String uid, UUID skillId, String level, Integer requested) {
        access.candidate(uid);
        QuestionGenerator.Result r = generator.generate(skill(skillId), level,
                requested == null ? 3 : requested);
        return new GenerateResponse(r.questions().size(), r.passagesUsed(),
                r.questions().stream().map(Question::getQuestionText).toList());
    }

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

    private static String key(UUID skillId, String level) { return skillId + ":" + level; }

    private Map<String, LevelProgress> progressMap(UUID profileId) {
        return progresses.findByProfileId(profileId).stream()
                .collect(Collectors.toMap(
                        p -> key(p.getSkillId(), p.getLevel()),
                        p -> p,
                        (x, y) -> x));
    }

    private LevelProgress progress(UUID profileId, UUID skillId, String level) {
        return progresses.findByProfileIdAndSkillIdAndLevel(profileId, skillId, level)
                .orElseGet(() -> {
                    LevelProgress p = new LevelProgress();
                    p.setProfileId(profileId);
                    p.setSkillId(skillId);
                    p.setLevel(level);
                    return progresses.save(p);
                });
    }

    /** Débutant ouvert ; un niveau s'ouvre quand le précédent est PASSED (5 listes + score global >= 60 %). */
    private static boolean isUnlocked(Map<String, LevelProgress> prog, UUID skillId, String level) {
        int idx = LEVELS.indexOf(level);
        if (idx < 0) return false;
        if (idx == 0) return true;
        LevelProgress prev = prog.get(key(skillId, LEVELS.get(idx - 1)));
        return prev != null && "PASSED".equals(prev.getStatus());
    }

    private void raiseSkillLevel(UUID profileId, Assessment a) {
        String current = store.currentLevel(profileId, a.getSkillId());
        if (rank(a.getLevel()) > rank(current)) {
            store.saveSkillLevel(profileId, a.getSkillId(), a.getLevel());
        }
    }

    private List<UUID> idsOf(Assessment a) {
        return Arrays.stream(a.getQuestionIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
    }

    /** Questions d'un test, dans l'ordre d'origine (important pour la reprise). */
    private List<Question> orderedQuestions(Assessment a) {
        Map<UUID, Question> byId = new HashMap<>();
        questions.findAllById(idsOf(a)).forEach(q -> byId.put(q.getId(), q));
        return idsOf(a).stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<UUID, Integer> readDraft(Assessment a) {
        if (a.getDraftAnswers() == null || a.getDraftAnswers().isBlank()) return Map.of();
        try {
            return mapper.readValue(a.getDraftAnswers(), new TypeReference<Map<UUID, Integer>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

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
        } catch (RuntimeException ignored) { }
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
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException("JSON invalide", e); }
    }
}