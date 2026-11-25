package com.skillmirror.common.progress;

import com.skillmirror.scoring.GapAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisLauncher {

    private final AnalysisProgressService progress;
    private final GapAnalysisService gapAnalysisService;
    private final CvAnalysisJob cvJob;
    private final GoalAnalysisJob goalJob;

    public synchronized void startCv(String uid, UUID cvId) {
        String key = AnalysisProgressService.cvKey(cvId);
        if (progress.isRunning(key)) return;
        progress.queued(key);
        try {
            cvJob.run(uid, cvId);
        } catch (TaskRejectedException e) {
            progress.fail(key, "File d'attente pleine, réessaie dans un instant.");
        }
    }

    /** force = true après confirmation du CV ; false = seulement si rien n'existe. */
    public synchronized AnalysisProgressService.Progress ensureGoal(UUID profileId, boolean force) {
        String key = AnalysisProgressService.goalKey(profileId);
        if (progress.isRunning(key)) return progress.get(key);

        if (!force && gapAnalysisService.hasRequirements(profileId)) {
            progress.done(key);
            return progress.get(key);
        }
        progress.queued(key);
        try {
            goalJob.run(profileId);
        } catch (TaskRejectedException e) {
            progress.fail(key, "File d'attente pleine, réessaie dans un instant.");
        }
        return progress.get(key);
    }

    @EventListener
    public void onCvConfirmed(CvConfirmedEvent event) {
        ensureGoal(event.profileId(), true);
    }
    public AnalysisProgressService.Progress cvProgress(UUID cvId) {
        return progress.get(AnalysisProgressService.cvKey(cvId));
    }
}