package com.skillmirror.scoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public interface CandidateSkillLevelRepository extends JpaRepository<CandidateSkillLevel, UUID> {
    List<CandidateSkillLevel> findByProfileId(UUID profileId);
    @Transactional
    void deleteByProfileId(UUID profileId);
}