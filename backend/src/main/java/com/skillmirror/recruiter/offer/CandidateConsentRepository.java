package com.skillmirror.recruiter.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateConsentRepository extends JpaRepository<CandidateConsent, UUID> {
    Optional<CandidateConsent> findByProfileId(UUID profileId);
    List<CandidateConsent> findByShareWithRecruitersTrue();
}
