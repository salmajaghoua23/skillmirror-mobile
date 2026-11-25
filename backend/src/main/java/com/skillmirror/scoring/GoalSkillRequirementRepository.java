package com.skillmirror.scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface GoalSkillRequirementRepository
        extends JpaRepository<GoalSkillRequirement, UUID> {

    boolean existsByProfileId(UUID profileId);

    List<GoalSkillRequirement> findByProfileId(UUID profileId);

    @Transactional
    void deleteByProfileId(UUID profileId);
}