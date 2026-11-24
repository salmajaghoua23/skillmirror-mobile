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
        CandidateScore last = scoreRepo.findTopByProfileIdOrderByCreatedAtDesc(profileId)
                .orElseGet(() -> scoreService.recompute(profileId, "INITIAL"));
        var history = scoreRepo.findByProfileIdOrderByCreatedAtAsc(profileId).stream().map(this::toDto).toList();
        return new ScoringDtos.DashboardDto(toDto(last), history,
                gapService.compareWithGoal(profileId), recoService.list(profileId));
    }

    @GetMapping("/{profileId}/missing-skills")
    public List<ScoringDtos.MissingSkillDto> missing(@PathVariable UUID profileId) {
        return gapService.findMissingSkills(profileId);
    }

    @PostMapping("/{profileId}/refresh")
    public ScoringDtos.ScoreDto refresh(@PathVariable UUID profileId) {
        return toDto(scoreService.recompute(profileId, "ASSESSMENT"));
    }

    private ScoringDtos.ScoreDto toDto(CandidateScore s) {
        return new ScoringDtos.ScoreDto(s.getGlobalScore(), s.getCvScore(), s.getFormScore(),
                s.getAssessmentScore(), s.getCreatedAt());
    }
}