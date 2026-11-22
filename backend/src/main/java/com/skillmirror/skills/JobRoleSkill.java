package com.skillmirror.skills;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "job_role_skills")
@Getter
@Setter
@NoArgsConstructor
public class JobRoleSkill {
    @Id
    private UUID id;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "skill_id")
    private UUID skillId;

    private Boolean required = true;
}