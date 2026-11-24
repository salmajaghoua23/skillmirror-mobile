package com.skillmirror.scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CandidateScoreRepository extends JpaRepository<CandidateScore, UUID> {
    Optional<CandidateScore> findTopByProfileIdOrderByCreatedAtDesc(UUID profileId);
    List<CandidateScore> findByProfileIdOrderByCreatedAtAsc(UUID profileId);
}