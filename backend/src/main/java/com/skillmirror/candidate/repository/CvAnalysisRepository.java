package com.skillmirror.candidate.repository;
import com.skillmirror.candidate.entity.CvAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CvAnalysisRepository extends JpaRepository<CvAnalysis, UUID> {
    Optional<CvAnalysis> findByCvId(UUID cvId);
}