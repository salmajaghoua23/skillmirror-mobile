package com.skillmirror.candidate.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.exception.CvProcessingException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_analyses")
public class CvAnalysis {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cv_id", nullable = false, unique = true)
    private UUID cvId;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(name = "data", columnDefinition = "text")
    private String dataJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    @PrePersist
    void touch() { updatedAt = Instant.now(); }

    public CvAnalysisData getData() {
        if (dataJson == null || dataJson.isBlank()) return null;
        try {
            return MAPPER.readValue(dataJson, CvAnalysisData.class).normalized();
        } catch (Exception e) {
            throw new CvProcessingException("Données d'analyse illisibles.", e);
        }
    }

    public void setData(CvAnalysisData data) {
        try {
            this.dataJson = data == null ? null : MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new CvProcessingException("Impossible d'enregistrer les données d'analyse.", e);
        }
    }

    public UUID getId() { return id; }
    public UUID getCvId() { return cvId; }
    public void setCvId(UUID cvId) { this.cvId = cvId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}