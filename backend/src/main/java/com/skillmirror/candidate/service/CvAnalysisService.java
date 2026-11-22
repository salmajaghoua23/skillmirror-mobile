package com.skillmirror.candidate.service;

import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.dto.CvAnalysisResponse;
import com.skillmirror.candidate.entity.CandidateCv;
import com.skillmirror.candidate.entity.CvAnalysis;
import com.skillmirror.candidate.exception.CvProcessingException;
import com.skillmirror.candidate.repository.CandidateCvRepository;
import com.skillmirror.candidate.repository.CvAnalysisRepository;
import com.skillmirror.common.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvAnalysisService {

    private final CandidateAccess access;
    private final CandidateCvRepository cvRepository;
    private final CvAnalysisRepository analysisRepository;
    private final CvStorageService storageService;
    private final CvExtractionService extractionService;
    private final CvAiAnalysisService aiService;

    /** Analyse du dernier CV du candidat connecté (pour le dashboard). */
    public CvAnalysisResponse latest(String uid) {
        Profile p = access.candidate(uid);
        return cvRepository.findFirstByProfileIdOrderByUploadedAtDesc(p.getId())
                .map(cv -> get(uid, cv.getId()))
                .orElse(null);
    }

    public CvAnalysisResponse analyze(String uid, UUID cvId) {
        CandidateCv cv = getOwnedCv(uid, cvId);
        CvAnalysis analysis = analysisRepository.findByCvId(cvId).orElseGet(() -> {
            CvAnalysis a = new CvAnalysis();
            a.setCvId(cvId);
            return a;
        });

        analysis.setStatus("PROCESSING");
        analysis.setErrorMessage(null);
        analysisRepository.save(analysis);

        try {
            byte[] bytes = storageService.download(cv.getStoragePath());
            String text = extractionService.extractText(bytes, cv.getFileName());
            analysis.setRawText(text);
            analysis.setData(aiService.analyze(text));
            analysis.setStatus("EXTRACTED");
            analysis.setConfirmedAt(null);
        } catch (CvProcessingException e) {
            analysis.setStatus("FAILED");
            analysis.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            analysis.setStatus("FAILED");
            analysis.setErrorMessage("Erreur inattendue pendant l'analyse du CV.");
        }
        return toResponse(analysisRepository.save(analysis));
    }

    public CvAnalysisResponse get(String uid, UUID cvId) {
        getOwnedCv(uid, cvId);
        return analysisRepository.findByCvId(cvId).map(this::toResponse)
                .orElse(new CvAnalysisResponse(cvId, "PENDING", CvAnalysisData.empty(), null, null));
    }

    public CvAnalysisResponse update(String uid, UUID cvId, CvAnalysisData newData) {
        getOwnedCv(uid, cvId);
        CvAnalysis a = analysisRepository.findByCvId(cvId)
                .orElseThrow(() -> new CvProcessingException("Aucune analyse à modifier. Lance d'abord l'analyse."));
        a.setData(newData.normalized());
        a.setStatus("EXTRACTED");
        a.setConfirmedAt(null);
        return toResponse(analysisRepository.save(a));
    }

    public CvAnalysisResponse confirm(String uid, UUID cvId) {
        getOwnedCv(uid, cvId);
        CvAnalysis a = analysisRepository.findByCvId(cvId)
                .orElseThrow(() -> new CvProcessingException("Aucune analyse à confirmer."));
        if (a.getData() == null || "FAILED".equals(a.getStatus())) {
            throw new CvProcessingException("Impossible de confirmer : l'extraction a échoué.");
        }
        a.setStatus("CONFIRMED");
        a.setConfirmedAt(Instant.now());
        return toResponse(analysisRepository.save(a));
    }

    private CandidateCv getOwnedCv(String uid, UUID cvId) {
        Profile p = access.candidate(uid);
        CandidateCv cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new CvProcessingException("CV introuvable."));
        if (!p.getId().equals(cv.getProfileId())) {
            throw new CvProcessingException("Ce CV ne t'appartient pas.");
        }
        return cv;
    }

    private CvAnalysisResponse toResponse(CvAnalysis a) {
        return new CvAnalysisResponse(a.getCvId(), a.getStatus(),
                a.getData() == null ? CvAnalysisData.empty() : a.getData(),
                a.getErrorMessage(), a.getConfirmedAt());
    }
}