
package com.skillmirror.scoring;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final Logger log =
            LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRepository recoRepo;
    private final GapAnalysisService gapService;
    private final GoalSkillRequirementRepository requirementRepo;

    private static final int MAX_RECOMMENDATIONS = 5;

    // Générer uniquement si aucune recommandation active n'existe
    @Transactional
    public void generateIfNeeded(UUID profileId) {

        List<Recommendation> existing =
                recoRepo.findByProfileIdAndActiveTrueOrderByCreatedAtDesc(
                        profileId
                );

        if (!existing.isEmpty()) {
            log.info(
                    "Recommandations déjà enregistrées pour {}",
                    profileId
            );
            return;
        }

        generate(profileId);
    }

    // Génération ou régénération forcée
    @Transactional
    public void generate(UUID profileId) {

        // Désactiver les anciennes recommandations
        recoRepo.deactivateAll(profileId);

        // Compétences manquantes ou insuffisantes
        List<ScoringDtos.MissingSkillDto> missingSkills =
                gapService.findMissingSkills(profileId);

        // Récupérer les exigences sauvegardées
        Map<UUID, Boolean> requiredMap =
                requirementRepo.findByProfileId(profileId)
                        .stream()
                        .collect(Collectors.toMap(
                                GoalSkillRequirement::getSkillId,
                                r -> Boolean.TRUE.equals(r.getRequired()),
                                (a, b) -> a
                        ));

        // Priorité : obligatoires d'abord, puis plus grand écart
        List<ScoringDtos.MissingSkillDto> prioritized =
                missingSkills.stream()
                        .collect(Collectors.toMap(
                                ScoringDtos.MissingSkillDto::skillId,
                                Function.identity(),
                                (a, b) -> a,
                                LinkedHashMap::new
                        ))
                        .values()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                (ScoringDtos.MissingSkillDto m) ->
                                                        requiredMap.getOrDefault(
                                                                m.skillId(), false
                                                        )
                                        )
                                        .reversed()
                                        .thenComparing(
                                                Comparator.comparingInt(
                                                        (ScoringDtos.MissingSkillDto m) ->
                                                                m.requiredLevel()
                                                                        - m.currentLevel()
                                                ).reversed()
                                        )
                        )
                        .limit(MAX_RECOMMENDATIONS)
                        .toList();

        List<Recommendation> recommendations =
                new ArrayList<>();

        for (ScoringDtos.MissingSkillDto skill : prioritized) {

            int gap =
                    skill.requiredLevel() - skill.currentLevel();

            Recommendation recommendation =
                    new Recommendation();

            recommendation.setProfileId(profileId);
            recommendation.setSkillId(skill.skillId());
            recommendation.setActive(true);

            recommendation.setPriority(
                    gap >= 40 ? "HIGH"
                            : gap >= 20 ? "MEDIUM"
                            : "LOW"
            );

            if (skill.currentLevel() == 0) {

                recommendation.setType("LEARN");

                recommendation.setMessage(
                        "Commencez à apprendre " + skill.skillName()
                                + ". Cette compétence est nécessaire pour "
                                + "votre objectif professionnel. "
                                + "Niveau requis : "
                                + skill.requiredLevel() + "%."
                );

            } else {

                recommendation.setType("IMPROVE");

                recommendation.setMessage(
                        "Améliorez votre niveau en "
                                + skill.skillName()
                                + ". Niveau actuel : "
                                + skill.currentLevel()
                                + "%, niveau requis : "
                                + skill.requiredLevel() + "%."
                );
            }

            recommendations.add(recommendation);
        }

        recoRepo.saveAll(recommendations);

        log.info(
                "{} recommandations sauvegardées pour le profil {}",
                recommendations.size(),
                profileId
        );
    }


    public List<ScoringDtos.RecommendationDto> list(
            UUID profileId) {

        return recoRepo
                .findByProfileIdAndActiveTrueOrderByCreatedAtDesc(
                        profileId
                )
                .stream()
                .map(r -> new ScoringDtos.RecommendationDto(
                        r.getId(),
                        r.getType(),
                        r.getPriority(),
                        r.getMessage(),
                        r.getSkillId()
                ))
                .toList();
    }
}
