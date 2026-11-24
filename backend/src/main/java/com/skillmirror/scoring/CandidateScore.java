package com.skillmirror.scoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "candidate_scores")
@Getter @Setter @NoArgsConstructor
public class CandidateScore {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "global_score") private double globalScore;
    @Column(name = "cv_score") private double cvScore;
    @Column(name = "form_score") private double formScore;
    @Column(name = "assessment_score") private double assessmentScore;
    private String source; // INITIAL | ASSESSMENT | TRAINING
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { createdAt = Instant.now(); }
}