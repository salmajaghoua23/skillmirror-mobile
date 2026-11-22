package com.skillmirror.recruiter.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {
    List<JobOffer> findByRecruiterIdOrderByCreatedAtDesc(UUID recruiterId);
    List<JobOffer> findByRecruiterIdAndStatusOrderByCreatedAtDesc(UUID recruiterId, String status);
    Optional<JobOffer> findByIdAndRecruiterId(UUID id, UUID recruiterId);
    List<JobOffer> findByStatusOrderByCreatedAtDesc(String status);
}
