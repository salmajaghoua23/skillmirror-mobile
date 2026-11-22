package com.skillmirror.skills;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRoleSkillRepository extends JpaRepository<JobRoleSkill, UUID> {
    List<JobRoleSkill> findByRoleId(UUID roleId);
}