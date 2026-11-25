package com.skillmirror.common.progress;

import com.skillmirror.scoring.GapAnalysisService;
import com.skillmirror.scoring.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalAnalysisJob {

    private final GapAnalysisService gapAnalysisService;
    private final ScoreService scoreService;
    private final AnalysisProgressService progress;

    @Async("aiExecutor")
    public void run(UUID profileId) {
        String key = AnalysisProgressService.goalKey(profileId);
        progress.bind(key);
        try {
            progress.update(5, "Préparation");
            gapAnalysisService.refreshGoalAnalysis(profileId);

            progress.update(95, "Calcul du score…");
            scoreService.recompute(profileId, "INITIAL");

            progress.done(key);
        } catch (Exception e) {
            progress.fail(key, e.getMessage());
        } finally {
            progress.unbind();
        }
    }
}