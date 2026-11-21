package com.skillmirror.candidate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.skillmirror.candidate.entity.CandidateCv;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateCvRepository extends JpaRepository<CandidateCv, UUID> {
    List<CandidateCv> findByProfileIdOrderByUploadedAtDesc(UUID profileId);
    Optional<CandidateCv> findByIdAndProfileId(UUID id, UUID profileId);
    Optional<CandidateCv> findFirstByProfileIdAndMainTrueOrderByUploadedAtDesc(UUID profileId);
    Optional<CandidateCv> findFirstByProfileIdOrderByUploadedAtDesc(UUID profileId);
}