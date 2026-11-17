package com.skillmirror.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateCvRepository extends JpaRepository<CandidateCv, UUID> {
    List<CandidateCv> findByProfileIdOrderByUploadedAtDesc(UUID profileId);
    Optional<CandidateCv> findByIdAndProfileId(UUID id, UUID profileId);
}