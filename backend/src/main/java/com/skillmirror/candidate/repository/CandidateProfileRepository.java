package com.skillmirror.candidate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.skillmirror.candidate.entity.CandidateProfile;
import java.util.Optional;
import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    Optional<CandidateProfile> findByProfileId(UUID profileId);
}