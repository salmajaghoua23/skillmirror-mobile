package com.skillmirror.common.progress;

import com.skillmirror.candidate.dto.CvAnalysisResponse;
import com.skillmirror.candidate.service.CvAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvAnalysisJob {

    private final CvAnalysisService cvAnalysisService;
    private final AnalysisProgressService progress;

    @Async("aiExecutor")
    public void run(String uid, UUID cvId) {
        String key = AnalysisProgressService.cvKey(cvId);
        progress.bind(key);
        try {
            progress.update(5, "Lecture du CV");
            CvAnalysisResponse r = cvAnalysisService.analyze(uid, cvId);
            if ("FAILED".equals(r.status())) {
                progress.fail(key, r.errorMessage());
            } else {
                progress.done(key);
            }
        } catch (Exception e) {
            progress.fail(key, e.getMessage());
        } finally {
            progress.unbind();
        }
    }
}