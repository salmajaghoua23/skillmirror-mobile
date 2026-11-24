
package com.skillmirror.scoring;

import com.skillmirror.assessment.Assessment;
import com.skillmirror.assessment.AssessmentRepository;
import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final CandidateScoreRepository scoreRepo;
    private final CandidateSkillLevelRepository levelRepo;
    private final CandidateProfileRepository profileRepo;
    private final AssessmentRepository assessmentRepo;
    private final SkillRepository skillRepo;
    private final CvSnapshotLoader cvLoader;
    private final RecommendationService recoService;

    /**
     * Calcul du score global du profil.
     *
     * Le score dépend uniquement :
     * - du CV : 60 %
     * - du formulaire : 40 %
     *
     * Les évaluations ne modifient jamais le score global.
     *
     * Les évaluations servent uniquement à mettre à jour
     * les niveaux des compétences du candidat.
     */
    @Transactional
    public CandidateScore recompute(UUID profileId, String source) {

        var cv = cvLoader.load(profileId);

        // 1. Calcul du score CV
        double cvScore = computeCvScore(cv);

        // 2. Calcul du score formulaire
        double formScore = computeFormScore(profileId);

        // 3. Récupération des dernières évaluations
        Map<UUID, Integer> assessed =
                latestAssessmentLevels(profileId);

        // 4. Mise à jour des niveaux de compétences
        // Les évaluations remplacent les niveaux estimés du CV.
        rebuildSkillLevels(profileId, cv, assessed);

        // 5. Score global indépendant des tests
        double global = cvScore * 0.6 + formScore * 0.4;

        // 6. Sauvegarde du score
        CandidateScore score = new CandidateScore();

        score.setProfileId(profileId);
        score.setCvScore(round(cvScore));
        score.setFormScore(round(formScore));

        // Conservé comme indicateur informatif uniquement
        double assessmentScore = assessed.values()
                .stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        score.setAssessmentScore(round(assessmentScore));

        // Le score global ne dépend pas de assessmentScore
        score.setGlobalScore(round(global));
        score.setSource(source);

        scoreRepo.save(score);

        // 7. Génération des recommandations
        recoService.generateIfNeeded(profileId);

        return score;
    }

    // --------------------------------------------------
    // 1. Calcul du score CV (100 points)
    // --------------------------------------------------

    private double computeCvScore(
            CvSnapshotLoader.CvSnapshot cv) {

        double skills =
                Math.min(cv.skills().size() / 10.0, 1) * 40;

        double experience =
                Math.min(cv.experienceYears() / 5.0, 1) * 25;

        double education =
                cv.educationCount() > 0 ? 15 : 0;

        double languages =
                Math.min(cv.languageCount() / 3.0, 1) * 10;

        double certifications =
                Math.min(cv.certifications().size() / 2.0, 1) * 10;

        return skills
                + experience
                + education
                + languages
                + certifications;
    }

    // --------------------------------------------------
    // 2. Calcul du score formulaire (100 points)
    // --------------------------------------------------

    private double computeFormScore(UUID profileId) {

        CandidateProfile p = profileRepo
                .findByProfileId(profileId)
                .orElse(null);

        if (p == null) {
            return 0;
        }

        int filled = 0;
        int total = 13;

        if (p.getAge() != null) filled++;
        if (nb(p.getGender())) filled++;
        if (nb(p.getCountry())) filled++;
        if (nb(p.getRegion())) filled++;
        if (nb(p.getStudyField())) filled++;
        if (nb(p.getEducationLevel())) filled++;
        if (nb(p.getDiploma())) filled++;
        if (nb(p.getSector())) filled++;
        if (nb(p.getAvailability())) filled++;
        if (nb(p.getInterviewLanguage())) filled++;

        if (p.getInterests() != null
                && !p.getInterests().isEmpty()) {
            filled++;
        }

        if (nb(p.getTargetOfferDescription())
                || nb(p.getTrainingObjective())) {
            filled++;
        }

        if (nb(p.getCvStoragePath())) filled++;

        return filled * 100.0 / total;
    }

    // --------------------------------------------------
    // 3. Dernières évaluations par compétence
    // --------------------------------------------------

    /**
     * Récupère la dernière évaluation terminée
     * pour chaque compétence.
     *
     * Ces résultats servent uniquement aux niveaux
     * des compétences et aux recommandations.
     */
    private Map<UUID, Integer> latestAssessmentLevels(
            UUID profileId) {

        Map<UUID, Assessment> latest = new HashMap<>();

        for (Assessment assessment :
                assessmentRepo.findByProfileIdAndStatus(
                        profileId, "COMPLETED")) {

            if (assessment.getSkillId() == null
                    || assessment.getScore() == null) {
                continue;
            }

            latest.merge(
                    assessment.getSkillId(),
                    assessment,
                    (x, y) ->
                            time(y).isAfter(time(x)) ? y : x
            );
        }

        Map<UUID, Integer> result = new HashMap<>();

        latest.forEach((id, assessment) ->
                result.put(
                        id,
                        (int) Math.round(percent(assessment))
                )
        );

        return result;
    }

    private Instant time(Assessment assessment) {
        return assessment.getFinishedAt() != null
                ? assessment.getFinishedAt()
                : Instant.EPOCH;
    }

    /**
     * Convertit le résultat en pourcentage.
     * Exemple : 0.85 devient 85.
     */
    private double percent(Assessment assessment) {

        double score = assessment.getScore();

        return Math.max(
                0,
                Math.min(100, score <= 1.0 ? score * 100 : score)
        );
    }

    // --------------------------------------------------
    // 4. Reconstruction des niveaux des compétences
    // --------------------------------------------------

    /**
     * Les niveaux CV sont estimés initialement.
     * Les évaluations remplacent ces niveaux lorsqu'elles existent.
     *
     * Cela ne modifie pas les données originales du CV.
     */
    private void rebuildSkillLevels(
            UUID profileId,
            CvSnapshotLoader.CvSnapshot cv,
            Map<UUID, Integer> assessed) {

        Map<String, Skill> byName = new HashMap<>();

        for (Skill skill : skillRepo.findAll()) {

            if (skill.getName() != null) {
                byName.putIfAbsent(
                        norm(skill.getName()),
                        skill
                );
            }
        }

        Map<UUID, Integer> levels = new HashMap<>();
        Map<UUID, String> sources = new HashMap<>();

        // A. Compétences détectées dans le CV
        for (String name : cv.skills()) {

            Skill skill = byName.get(norm(name));

            if (skill == null) {
                continue;
            }

            levels.put(
                    skill.getId(),
                    estimateFromCv(cv, name)
            );

            sources.put(skill.getId(), "CV");
        }

        // B. Les évaluations remplacent les niveaux estimés
        assessed.forEach((id, level) -> {
            levels.put(id, level);
            sources.put(id, "ASSESSMENT");
        });

        // C. Sauvegarder les niveaux actuels
        levelRepo.deleteByProfileId(profileId);
        levelRepo.flush();

        List<CandidateSkillLevel> rows = new ArrayList<>();

        levels.forEach((skillId, level) -> {

            CandidateSkillLevel row =
                    new CandidateSkillLevel();

            row.setProfileId(profileId);
            row.setSkillId(skillId);
            row.setLevelScore(level);
            row.setLevelLabel(label(level));
            row.setSource(sources.get(skillId));

            rows.add(row);
        });

        levelRepo.saveAll(rows);
    }

    // --------------------------------------------------
    // 5. Estimation initiale du niveau depuis le CV
    // --------------------------------------------------

    private int estimateFromCv(
            CvSnapshotLoader.CvSnapshot cv,
            String skill) {

        String key = skill.toLowerCase(Locale.ROOT);

        int level = 25;

        long occurrences = cv.experienceTexts()
                .stream()
                .filter(text ->
                        text.toLowerCase(Locale.ROOT)
                                .contains(key)
                )
                .count();

        level += (int) Math.min(occurrences * 10, 20);

        if (cv.certifications().stream()
                .anyMatch(certificate ->
                        certificate.toLowerCase(Locale.ROOT)
                                .contains(key))) {
            level += 10;
        }

        if (cv.experienceYears() >= 3) {
            level += 5;
        }

        return Math.min(level, 60);
    }

    // --------------------------------------------------
    // 6. Libellés des niveaux
    // --------------------------------------------------

    static String label(int level) {

        return level < 40
                ? "DEBUTANT"
                : level < 70
                ? "INTERMEDIAIRE"
                : "AVANCE";
    }

    // --------------------------------------------------
    // 7. Normalisation des noms
    // --------------------------------------------------

    static String norm(String value) {

        String normalized = Normalizer.normalize(
                        value == null ? "" : value,
                        Normalizer.Form.NFD
                ).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);

        return normalized.replaceAll("[^a-z0-9+#]", "");
    }

    private static boolean nb(String value) {
        return value != null && !value.isBlank();
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
