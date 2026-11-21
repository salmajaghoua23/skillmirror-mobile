package com.skillmirror.candidate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidate_cvs")
@Getter @Setter @NoArgsConstructor
public class CandidateCv {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_path", nullable = false, unique = true)
    private String storagePath;

    @Column(name = "is_main")
    private Boolean main = false;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_at", updatable = false)
    private OffsetDateTime uploadedAt;

    @PrePersist
    void onCreate() { uploadedAt = OffsetDateTime.now(); }
}