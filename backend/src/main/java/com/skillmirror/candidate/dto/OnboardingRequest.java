package com.skillmirror.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OnboardingRequest(
        @NotNull @Valid CandidateProfileRequest profile,
        @NotNull @Valid PreferencesRequest preferences) {}