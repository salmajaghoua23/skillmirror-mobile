
package com.skillmirror.scoring;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoreService scoreService;
    private final GapAnalysisService gapService;
    private final RecommendationService recoService;
    private final CandidateScoreRepository scoreRepo;

    @PostMapping("/{profileId}/compute")
    public ScoringDtos.ScoreDto compute(@PathVariable UUID profileId) {
        return toDto(scoreService.recompute(profileId, "INITIAL"));
    }

    @GetMapping("/{profileId}/dashboard")
    public ScoringDtos.DashboardDto dashboard(@PathVariable UUID profileId) {

        CandidateScore last = scoreRepo
                .findTopByProfileIdOrderByCreatedAtDesc(profileId)
                .orElseGet(() -> scoreService.recompute(profileId, "INITIAL"));

        var history = scoreRepo
                .findByProfileIdOrderByCreatedAtAsc(profileId)
                .stream()
                .map(this::toDto)
                .toList();

        // Récupère les exigences sauvegardées.
        // Ne doit pas appeler Ollama si elles existent déjà.
        var goal = gapService.compareWithGoal(profileId);

        // Récupère les recommandations déjà sauvegardées.
        recoService.generateIfNeeded(profileId);

        var recommendations = recoService.list(profileId);

        return new ScoringDtos.DashboardDto(
                toDto(last),
                history,
                goal,
                recommendations
        );
    }

    @GetMapping("/{profileId}/missing-skills")
    public List<ScoringDtos.MissingSkillDto> missing(
            @PathVariable UUID profileId) {

        // Utilise les exigences sauvegardées.
        return gapService.findMissingSkills(profileId);
    }

    @PostMapping("/{profileId}/refresh")
    public ScoringDtos.ScoreDto refresh(
            @PathVariable UUID profileId) {

        // Force une nouvelle analyse Ollama.
        // Les anciennes exigences seront remplacées.
        gapService.refreshGoalAnalysis(profileId);

        // Régénère les recommandations à partir des nouveaux résultats.
        recoService.generate(profileId);

        // Recalcule le score.
        return toDto(scoreService.recompute(profileId, "ASSESSMENT"));
    }

    private ScoringDtos.ScoreDto toDto(CandidateScore s) {
        return new ScoringDtos.ScoreDto(
                s.getGlobalScore(),
                s.getCvScore(),
                s.getFormScore(),
                s.getAssessmentScore(),
                s.getCreatedAt()
        );
    }
}
