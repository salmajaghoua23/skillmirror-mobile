package com.skillmirror.candidate.dto;

import jakarta.validation.constraints.*;

public record CandidateProfileRequest(
        @NotBlank String fullName,
        @NotNull @Min(16) @Max(80) Integer age,
        @NotBlank @Pattern(regexp = "M|F|NOT_SPECIFIED", message = "gender: M, F ou NOT_SPECIFIED") String gender,
        String country,
        String region,
        String studyField,
        String educationLevel,
        String diploma) {}