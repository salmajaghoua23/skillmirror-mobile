
package com.skillmirror.scoring;

import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recoRepo;
    private final GapAnalysisService gapService;
    private final CandidateSkillLevelRepository levelRepo;
    private final SkillRepository skillRepo;

    @Transactional
    public void generate(UUID profileId) {

        // Désactiver les anciennes recommandations
        recoRepo.deactivateAll(profileId);

        List<Recommendation> list = new ArrayList<>();

        // Éviter plusieurs recommandations pour la même compétence
        Set<UUID> recommendedSkills = new HashSet<>();

        // Récupérer les noms des compétences
        Map<UUID, String> skillNames = skillRepo.findAll()
                .stream()
                .collect(Collectors.toMap(
                        Skill::getId,
                        s -> s.getName() == null ? "Compétence" : s.getName(),
                        (a, b) -> a
                ));

        // 1. Compétences absentes ou insuffisantes
        for (var m : gapService.findMissingSkills(profileId)) {

            if (!recommendedSkills.add(m.skillId())) {
                continue;
            }

            int gap = m.requiredLevel() - m.currentLevel();

            Recommendation r = new Recommendation();
            r.setProfileId(profileId);
            r.setSkillId(m.skillId());

            r.setPriority(
                    gap >= 40 ? "HIGH" :
                            gap >= 20 ? "MEDIUM" : "LOW"
            );

            if (m.currentLevel() == 0) {
                r.setType("LEARN");
                r.setMessage(
                        "Apprenez " + m.skillName()
                                + " : cette compétence est requise pour votre objectif."
                );
            } else {
                r.setType("IMPROVE");
                r.setMessage(
                        "Améliorez " + m.skillName()
                                + " : votre niveau est de " + m.currentLevel()
                                + "% et le niveau requis est de "
                                + m.requiredLevel() + "%."
                );
            }

            list.add(r);
        }

        // 2. Compétences issues du CV qui n'ont pas encore
        // une recommandation LEARN ou IMPROVE
        levelRepo.findByProfileId(profileId).stream()
                .filter(l -> "CV".equals(l.getSource()))
                .filter(l -> recommendedSkills.add(l.getSkillId()))
                .forEach(l -> {

                    String name = skillNames.getOrDefault(
                            l.getSkillId(), "cette compétence"
                    );

                    Recommendation r = new Recommendation();
                    r.setProfileId(profileId);
                    r.setSkillId(l.getSkillId());
                    r.setType("ASSESS");
                    r.setPriority("LOW");
                    r.setMessage(
                            "Passez un test pour évaluer votre niveau en "
                                    + name + "."
                    );

                    list.add(r);
                });

        recoRepo.saveAll(list);
    }

    public List<ScoringDtos.RecommendationDto> list(UUID profileId) {
        return recoRepo
                .findByProfileIdAndActiveTrueOrderByCreatedAtDesc(profileId)
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
