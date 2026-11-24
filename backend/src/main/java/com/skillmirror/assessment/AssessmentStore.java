package com.skillmirror.assessment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Écritures natives : colonnes jsonb de assessments (results, strengths, weaknesses)
 * et table candidate_skills (niveau de chaque compétence du candidat).
 */
@Component
public class AssessmentStore {

    @PersistenceContext
    private EntityManager em;

    /** Enregistre la correction : score, statut, date et détails (jsonb). */
    @Transactional
    public void saveResult(UUID assessmentId, double score, String resultsJson,
                           String strengthsJson, String weaknessesJson) {
        em.createNativeQuery(
                        "update assessments set score = cast(:score as numeric), status = 'COMPLETED', "
                                + "finished_at = now(), results = cast(:results as jsonb), "
                                + "strengths = cast(:strengths as jsonb), weaknesses = cast(:weaknesses as jsonb) "
                                + "where id = cast(:id as uuid)")
                .setParameter("score", score)
                .setParameter("results", resultsJson)
                .setParameter("strengths", strengthsJson)
                .setParameter("weaknesses", weaknessesJson)
                .setParameter("id", assessmentId.toString())
                .executeUpdate();
    }

    /** Détails de la correction (JSON) d'un test terminé. */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Optional<String> loadResults(UUID assessmentId) {
        List<Object> rows = em.createNativeQuery(
                        "select cast(results as text) from assessments where id = cast(:id as uuid)")
                .setParameter("id", assessmentId.toString())
                .getResultList();
        return rows.isEmpty() || rows.get(0) == null
                ? Optional.empty() : Optional.of((String) rows.get(0));
    }

    /** Niveau actuel du candidat pour cette compétence (null s'il n'en a pas). */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public String currentLevel(UUID profileId, UUID skillId) {
        List<Object> rows = em.createNativeQuery(
                        "select level from candidate_skills "
                                + "where profile_id = cast(:p as uuid) and skill_id = cast(:s as uuid) limit 1")
                .setParameter("p", profileId.toString())
                .setParameter("s", skillId.toString())
                .getResultList();
        return rows.isEmpty() ? null : (String) rows.get(0);
    }

    /** Crée ou met à jour la ligne candidate_skills (profile, skill, level). */
    @Transactional
    public void saveSkillLevel(UUID profileId, UUID skillId, String level) {
        int updated = em.createNativeQuery(
                        "update candidate_skills set level = cast(:l as text) "
                                + "where profile_id = cast(:p as uuid) and skill_id = cast(:s as uuid)")
                .setParameter("l", level)
                .setParameter("p", profileId.toString())
                .setParameter("s", skillId.toString())
                .executeUpdate();
        if (updated == 0) {
            em.createNativeQuery(
                            "insert into candidate_skills (profile_id, skill_id, level) "
                                    + "values (cast(:p as uuid), cast(:s as uuid), cast(:l as text))")
                    .setParameter("p", profileId.toString())
                    .setParameter("s", skillId.toString())
                    .setParameter("l", level)
                    .executeUpdate();
        }
    }
}