package com.skillmirror.scoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "recommendations")
@Getter @Setter @NoArgsConstructor
public class Recommendation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "skill_id") private UUID skillId;
    private String type;     // LEARN | IMPROVE | ASSESS
    private String priority; // HIGH | MEDIUM | LOW
    @Column(length = 1000) private String message;
    private boolean active = true;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @PrePersist void onCreate() { createdAt = Instant.now(); }
}