package com.skillmirror.skills;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRoleRepository extends JpaRepository<JobRole, UUID> {
    List<JobRole> findAllByOrderByNameAsc();
}