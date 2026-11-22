package com.skillmirror.recruiter.offer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "job_offers")
@Getter
@Setter
@NoArgsConstructor
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recruiter_id", nullable = false)
    private UUID recruiterId;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String location;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "experience_level")
    private String experienceLevel;

    private Boolean remote = false;

    @Column(name = "salary_range")
    private String salaryRange;

    /** DRAFT | PUBLISHED | CLOSED */
    private String status = "DRAFT";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "offer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<JobOfferSkill> skills = new LinkedHashSet<>();

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
