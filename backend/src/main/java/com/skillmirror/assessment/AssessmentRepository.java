package com.skillmirror.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    List<Assessment> findByProfileIdOrderByCreatedAtDesc(UUID profileId);
    List<Assessment> findByProfileIdAndStatus(UUID profileId, String status);
}