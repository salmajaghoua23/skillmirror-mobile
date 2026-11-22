package com.skillmirror.recruiter.offer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tous les objets d'entrée/sortie de la Phase 7 (un seul fichier pour rester simple). */
public final class OfferDtos {

    private OfferDtos() {}

    // ---------- Entrées ----------

    public record SkillInput(
            @NotBlank(message = "Nom de compétence obligatoire") @Size(max = 80) String name,
            Boolean required) {}

    public record OfferRequest(
            @NotBlank(message = "Titre obligatoire") @Size(max = 150) String title,
            @NotBlank(message = "Description obligatoire") @Size(max = 5000) String description,
            @Size(max = 150) String location,
            @NotBlank(message = "Type de contrat obligatoire")
            @Pattern(regexp = "CDI|CDD|STAGE|ALTERNANCE|FREELANCE", message = "Type de contrat invalide")
            String contractType,
            @Pattern(regexp = "^(JUNIOR|INTERMEDIATE|SENIOR)?$", message = "Niveau invalide")
            String experienceLevel,
            Boolean remote,
            @Size(max = 100) String salaryRange,
            @Pattern(regexp = "^(DRAFT|PUBLISHED|CLOSED)?$", message = "Statut invalide")
            String status,
            @Valid List<SkillInput> skills) {}

    public record OfferStatusRequest(
            @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|CLOSED", message = "Statut invalide")
            String status) {}

    public record ApplicationStatusRequest(
            @NotBlank @Pattern(regexp = "SENT|VIEWED|SHORTLISTED|ACCEPTED|REJECTED",
                    message = "Statut invalide")
            String status) {}

    public record ApplyRequest(@Size(max = 2000) String message) {}

    public record ConsentRequest(Boolean share) {}

    // ---------- Sorties ----------

    public record SkillOut(String name, boolean required) {}

    public record OfferResponse(
            UUID id, String title, String description, String location,
            String contractType, String experienceLevel, boolean remote,
            String salaryRange, String status, String companyName,
            List<SkillOut> skills, long applicationsCount,
            Instant createdAt, Instant updatedAt) {}

    public record ApplicationResponse(
            UUID id, UUID offerId, String offerTitle,
            UUID candidateId, String candidateName,
            String status, String message, Integer matchScore, Instant createdAt) {}

    public record CandidateSummary(
            UUID profileId, String fullName, String sector, String country,
            String region, String educationLevel, String availability,
            int matchScore, List<String> matchedSkills, List<String> missingSkills) {}

    public record CandidateDetail(
            UUID profileId, String fullName, String email,
            String country, String region, String studyField, String educationLevel,
            String diploma, String sector, String availability, Boolean mobility,
            List<String> technicalSkills, List<String> softSkills,
            List<EducationOut> education, List<ExperienceOut> experiences,
            List<LanguageOut> languages, List<CertificationOut> certifications,
            String summary) {}

    public record EducationOut(String degree, String institution, String field,
                               String startYear, String endYear) {}
    public record ExperienceOut(String title, String company, String startDate,
                                String endDate, String description) {}
    public record LanguageOut(String name, String level) {}
    public record CertificationOut(String name, String issuer, String year) {}

    public record DashboardResponse(
            String fullName, String companyName,
            long totalOffers, long publishedOffers, long draftOffers, long closedOffers,
            long totalApplications, long newApplications, long shortlisted,
            long accepted, long rejected,
            List<ApplicationResponse> recentApplications) {}
}
