package com.skillmirror.assessment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmirror.assessment.AssessmentDtos.*;
import com.skillmirror.candidate.service.CandidateAccess;
import com.skillmirror.common.Profile;
import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import com.skillmirror.scoring.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Évaluations par compétence : création (3B), correction, résultats et historique (3C). */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final int DEFAULT_COUNT = 5;
    /** Score minimum (en %) pour valider un niveau. */
    private static final double PASS_SCORE = 70.0;
    private static final Map<String, Integer> LEVEL_RANK =
            Map.of("BEGINNER", 1, "INTERMEDIATE", 2, "ADVANCED", 3);

    private final CandidateAccess access;
    private final SkillRepository skills;
    private final QuestionRepository questions;
    private final AssessmentRepository assessments;
    private final QuestionGenerator generator;
    private final AssessmentStore store;
    private final ScoreService scoreService;

    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // 3B : création des évaluations
    // ------------------------------------------------------------------

    /** Compétences pour lesquelles au moins une question existe. */
    public List<AvailableSkill> available(String uid) {
        access.candidate(uid);
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : questions.countBySkill()) counts.put((UUID) row[0], (Long) row[1]);

        return skills.findAllById(counts.keySet()).stream()
                .map(s -> new AvailableSkill(s.getId(), s.getName(), s.getCategory(), counts.get(s.getId())))
                .sorted(Comparator.comparing(AvailableSkill::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Démarre une évaluation : tire des questions (banque + IA), SANS les bonnes réponses. */
    public StartResponse start(String uid, UUID skillId, String level, Integer requested) {
        Profile me = access.candidate(uid);
        Skill skill = skill(skillId);
        int count = requested == null ? DEFAULT_COUNT : requested;

        List<Question> pool = new ArrayList<>(questions.findBySkillIdAndLevel(skillId, level));

        if (pool.isEmpty()) {
            // Rien en stock : on génère maintenant avec l'IA (le candidat attend)
            pool.addAll(generator.generate(skill, level, count).questions());
        } else if (pool.size() < count) {
            // Un peu de stock : on démarre avec, et l'IA complète en arrière-plan pour la prochaine fois
            generator.generateAsync(skill, level, count);
        }
        if (pool.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucune question disponible pour cette compétence à ce niveau");
        }

        Collections.shuffle(pool);
        List<Question> picked = pool.subList(0, Math.min(count, pool.size()));

        Assessment a = new Assessment();
        a.setProfileId(me.getId());
        a.setSkillId(skillId);
        a.setLevel(level);
        a.setStatus("IN_PROGRESS");
        a.setTotalQuestions(picked.size());
        a.setQuestionIds(picked.stream().map(q -> q.getId().toString())
                .collect(Collectors.joining(",")));
        assessments.save(a);

        List<QuestionOut> out = picked.stream()
                .map(q -> new QuestionOut(q.getId(), q.getQuestionText(), q.options()))
                .toList();
        return new StartResponse(a.getId(), skill.getName(), level, out);
    }

    /** Génération manuelle avec le RAG (utile pour tester / enrichir la banque). */
    public GenerateResponse generate(String uid, UUID skillId, String level, Integer requested) {
        access.candidate(uid);
        Skill skill = skill(skillId);
        int count = requested == null ? DEFAULT_COUNT : requested;
        QuestionGenerator.Result r = generator.generate(skill, level, count);
        return new GenerateResponse(r.questions().size(), r.passagesUsed(),
                r.questions().stream().map(Question::getQuestionText).toList());
    }

    // ------------------------------------------------------------------
    // 3C : correction automatique et calcul du résultat
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

        // Correction : comparaison avec la bonne réponse, dans l'ordre du test
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

        // Points forts / à améliorer (lus plus tard par le calcul du score global)
        List<String> strengths = passed ? List.of(skillName) : List.of();
        List<String> weaknesses = passed ? List.of() : List.of(skillName);
        store.saveResult(a.getId(), score, json(details), json(strengths), json(weaknesses));

        // Recalculer les compétences et le score du candidat après l'évaluation
        scoreService.recompute(a.getProfileId(), "ASSESSMENT");

        // Le niveau du candidat dans candidate_skills ne fait que monter
        if (passed && a.getSkillId() != null) {
            String current = store.currentLevel(me.getId(), a.getSkillId());
            if (rank(a.getLevel()) > rank(current)) {
                store.saveSkillLevel(me.getId(), a.getSkillId(), a.getLevel());
            }
        }
        return new ResultResponse(a.getId(), skillName, a.getLevel(), score, correct, total,
                passed, "COMPLETED", Instant.now(), details);
    }

    /** Résultat détaillé d'un test terminé (correction et explications). */
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
        return new ResultResponse(a.getId(), skillName, a.getLevel(), score, correct, details.size(),
                score >= PASS_SCORE, a.getStatus(), a.getFinishedAt(), details);
    }

    // ------------------------------------------------------------------
    // 3C : historique des tests
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