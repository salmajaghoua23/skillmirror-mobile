package com.skillmirror.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LevelProgressRepository extends JpaRepository<LevelProgress, UUID> {
    Optional<LevelProgress> findByProfileIdAndSkillIdAndLevel(UUID profileId, UUID skillId, String level);
    List<LevelProgress> findByProfileId(UUID profileId);
}