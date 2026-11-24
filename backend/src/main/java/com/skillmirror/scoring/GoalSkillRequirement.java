
package com.skillmirror.scoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "goal_skill_requirements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_profile_goal_skill",
                columnNames = {"profile_id", "skill_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class GoalSkillRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "required_level", nullable = false)
    private Integer requiredLevel;

    @Column(name = "required", nullable = false)
    private Boolean required;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
