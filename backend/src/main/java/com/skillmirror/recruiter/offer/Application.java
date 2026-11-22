package com.skillmirror.recruiter.offer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Candidature d'un candidat à une offre. */
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    /** profiles.id du candidat */
    @Column(name = "candidate_profile_id", nullable = false)
    private UUID candidateProfileId;

    /** SENT | VIEWED | SHORTLISTED | ACCEPTED | REJECTED */
    private String status = "SENT";

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
