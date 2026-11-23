package com.skillmirror.assessment;

import com.skillmirror.assessment.AssessmentDtos.*;
import com.skillmirror.candidate.service.CandidateAccess;
import com.skillmirror.common.Profile;
import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Création des évaluations par compétence et par niveau. */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final int DEFAULT_COUNT = 5;

    private final CandidateAccess access;
    private final SkillRepository skills;
    private final QuestionRepository questions;
    private final AssessmentRepository assessments;
    private final QuestionGenerator generator;

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

    private Skill skill(UUID id) {
        return skills.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Compétence introuvable"));
    }
}