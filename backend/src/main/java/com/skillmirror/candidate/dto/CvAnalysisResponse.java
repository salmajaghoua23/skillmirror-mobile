package com.skillmirror.candidate.dto;

import java.time.Instant;
import java.util.UUID;

public record CvAnalysisResponse(
        UUID cvId,
        String status,
        CvAnalysisData data,
        String errorMessage,
        Instant confirmedAt) {}