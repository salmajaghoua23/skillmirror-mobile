package com.skillmirror.candidate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidate_profiles")
@Getter @Setter @NoArgsConstructor
public class CandidateProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private UUID profileId;

    // Informations personnelles
    private Integer age;
    private String gender; // M | F | NOT_SPECIFIED
    private String country;
    private String region;

    @Column(name = "study_field")
    private String studyField;

    @Column(name = "education_level")
    private String educationLevel;

    private String diploma;

    // Préférences professionnelles
    private String sector;
    private String availability;
    private Boolean mobility = false;

    @Column(name = "interview_language")
    private String interviewLanguage;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interests", columnDefinition = "text[]")
    private List<String> interests = new ArrayList<>();

    @Column(name = "target_offer_description")
    private String targetOfferDescription;

    @Column(name = "training_objective")
    private String trainingObjective;

    @Column(name = "cv_storage_path")
    private String cvStoragePath;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}