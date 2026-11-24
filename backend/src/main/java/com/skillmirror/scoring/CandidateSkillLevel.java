package com.skillmirror.scoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "candidate_skills")
@Getter @Setter @NoArgsConstructor
public class CandidateSkillLevel {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "skill_id", nullable = false) private UUID skillId;
    @Column(name = "level_score")
    private Integer levelScore; // 0..100
    @Column(name = "level") private String levelLabel; // DEBUTANT | INTERMEDIAIRE | AVANCE
    private String source; // CV | ASSESSMENT
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();
}