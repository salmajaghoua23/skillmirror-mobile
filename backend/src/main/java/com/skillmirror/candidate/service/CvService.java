package com.skillmirror.candidate.service;

import com.skillmirror.candidate.entity.CandidateCv;
import com.skillmirror.candidate.repository.CandidateCvRepository;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.dto.CvResponse;
import com.skillmirror.common.Profile;
import lombok.RequiredArgsConstructor;
import com.skillmirror.candidate.exception.CvProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvService {
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "docx");
    private final CandidateAccess access;
    private final CandidateCvRepository cvRepo;
    private final CandidateProfileRepository candidateRepo;
    private final CvStorageService storage;


    private CandidateCv owned(Profile p, UUID cvId) {
        return cvRepo.findByIdAndProfileId(cvId, p.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "CV introuvable"));
    }

    /** Recopie le chemin du CV principal dans candidate_profiles.cv_storage_path. */
    @Transactional
    public void syncCandidateCvPath(Profile p) {
        String path = cvRepo.findByProfileIdOrderByUploadedAtDesc(p.getId()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getMain()))
                .map(CandidateCv::getStoragePath)
                .findFirst().orElse(null);
        candidateRepo.findByProfileId(p.getId()).ifPresent(c -> {
            c.setCvStoragePath(path);
            candidateRepo.save(c);
        });
    }

    public List<CvResponse> list(String uid) {
        Profile p = access.candidate(uid);
        return cvRepo.findByProfileIdOrderByUploadedAtDesc(p.getId()).stream().map(CvResponse::from).toList();
    }

    @Transactional
    public CvResponse upload(String uid, MultipartFile file) {
        Profile p = access.candidate(uid);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide");
        }
        String original = file.getOriginalFilename() == null ? "cv"
                : file.getOriginalFilename().replaceAll("[\\\\/]", "_");
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formats acceptés : PDF, DOCX");
        }

        String path = p.getId() + "/" + UUID.randomUUID() + "." + ext;
        String contentType = ext.equals("pdf") ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        // 1) le fichier part d'abord dans le bucket
        try {
            storage.upload(path, file.getBytes(), contentType);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lecture du fichier impossible");
        } catch (CvProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }

        // 2) la ligne en base n'est créée qu'ensuite
        try {
            boolean first = cvRepo.findByProfileIdOrderByUploadedAtDesc(p.getId()).isEmpty();
            CandidateCv cv = new CandidateCv();
            cv.setProfileId(p.getId());
            cv.setFileName(original);
            cv.setStoragePath(path);
            cv.setSizeBytes(file.getSize());
            cv.setMain(first);
            cv = cvRepo.save(cv);
            syncCandidateCvPath(p);
            return CvResponse.from(cv);
        } catch (RuntimeException e) {
            storage.delete(path); // pas de fichier orphelin dans le bucket
            throw e;
        }
    }

    @Transactional
    public void setActive(String uid, UUID cvId) {
        Profile p = access.candidate(uid);
        owned(p, cvId);
        List<CandidateCv> all = cvRepo.findByProfileIdOrderByUploadedAtDesc(p.getId());
        all.forEach(c -> c.setMain(c.getId().equals(cvId)));
        cvRepo.saveAll(all);
        syncCandidateCvPath(p);
    }

    @Transactional
    public void delete(String uid, UUID cvId) {
        Profile p = access.candidate(uid);
        CandidateCv cv = owned(p, cvId);
        boolean wasMain = Boolean.TRUE.equals(cv.getMain());
        storage.delete(cv.getStoragePath());
        cvRepo.delete(cv);
        cvRepo.flush();

        if (wasMain) { // le plus récent des CV restants devient principal
            cvRepo.findByProfileIdOrderByUploadedAtDesc(p.getId()).stream().findFirst().ifPresent(c -> {
                c.setMain(true);
                cvRepo.save(c);
            });
        }
        syncCandidateCvPath(p);
    }
}