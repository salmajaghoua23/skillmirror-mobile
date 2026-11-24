package com.skillmirror.scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
    List<Recommendation> findByProfileIdAndActiveTrueOrderByCreatedAtDesc(UUID profileId);

    @Modifying @Transactional
    @Query("update Recommendation r set r.active = false where r.profileId = :pid")
    void deactivateAll(@Param("pid") UUID profileId);
}