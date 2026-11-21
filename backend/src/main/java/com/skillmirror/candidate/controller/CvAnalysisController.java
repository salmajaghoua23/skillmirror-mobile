package com.skillmirror.candidate.controller;

import com.skillmirror.account.CurrentUser;
import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.dto.CvAnalysisResponse;
import com.skillmirror.candidate.exception.CvProcessingException;
import com.skillmirror.candidate.service.CvAnalysisService;
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

    @GetMapping("/analysis/latest")
    public ResponseEntity<CvAnalysisResponse> latest() {
        CvAnalysisResponse r = service.latest(CurrentUser.uid());
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @PostMapping("/{cvId}/analysis/run")
    public CvAnalysisResponse run(@PathVariable UUID cvId) {
        return service.analyze(CurrentUser.uid(), cvId);
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
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage()));
    }
}