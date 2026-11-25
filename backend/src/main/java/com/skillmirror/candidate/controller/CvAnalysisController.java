package com.skillmirror.candidate.controller;

import com.skillmirror.account.CurrentUser;
import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.dto.CvAnalysisResponse;
import com.skillmirror.candidate.exception.CvProcessingException;
import com.skillmirror.candidate.service.CvAnalysisService;
import com.skillmirror.common.progress.AnalysisLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidate/cvs")
@RequiredArgsConstructor
public class CvAnalysisController {

    private final CvAnalysisService service;
    private final AnalysisLauncher launcher;

    @GetMapping("/analysis/latest")
    public ResponseEntity<CvAnalysisResponse> latest() {
        CvAnalysisResponse r = service.latest(CurrentUser.uid());
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    /** Lance l'analyse en arrière-plan : répond immédiatement (202). */
    @PostMapping("/{cvId}/analysis/run")
    public ResponseEntity<Map<String, Object>> run(@PathVariable UUID cvId) {
        String uid = CurrentUser.uid();
        service.get(uid, cvId);            // vérifie que le CV appartient bien à l'utilisateur
        launcher.startCv(uid, cvId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("cvId", cvId, "status", "QUEUED"));
    }

    /** Progression de l'analyse du CV (sondée par l'app toutes les 2 s). */
    @GetMapping("/{cvId}/analysis/progress")
    public Object progress(@PathVariable UUID cvId) {
        service.get(CurrentUser.uid(), cvId);   // contrôle d'accès
        return launcher.cvProgress(cvId);
    }

    @GetMapping("/{cvId}/analysis")
    public CvAnalysisResponse get(@PathVariable UUID cvId) {
        return service.get(CurrentUser.uid(), cvId);
    }

    @PutMapping("/{cvId}/analysis")
    public CvAnalysisResponse update(@PathVariable UUID cvId, @RequestBody CvAnalysisData body) {
        return service.update(CurrentUser.uid(), cvId, body);
    }

    @PostMapping("/{cvId}/analysis/confirm")
    public CvAnalysisResponse confirm(@PathVariable UUID cvId) {
        return service.confirm(CurrentUser.uid(), cvId);
    }

    @ExceptionHandler(CvProcessingException.class)
    public ResponseEntity<Map<String, String>> handle(CvProcessingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("message", e.getMessage()));
    }
}