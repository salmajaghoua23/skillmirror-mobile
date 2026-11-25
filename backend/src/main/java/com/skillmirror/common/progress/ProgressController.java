package com.skillmirror.common.progress;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final AnalysisProgressService progress;
    private final AnalysisLauncher launcher;

    @GetMapping("/goal/{profileId}")
    public AnalysisProgressService.Progress goal(@PathVariable UUID profileId) {
        return progress.get(AnalysisProgressService.goalKey(profileId));
    }

    /** Lance l'analyse du métier visé si elle n'existe pas encore. */
    @PostMapping("/goal/{profileId}/ensure")
    public AnalysisProgressService.Progress ensure(@PathVariable UUID profileId) {
        return launcher.ensureGoal(profileId, false);
    }
    @PostMapping("/goal/{profileId}/refresh")
    public AnalysisProgressService.Progress refresh(@PathVariable UUID profileId) {
        return launcher.ensureGoal(profileId, true);
    }
}