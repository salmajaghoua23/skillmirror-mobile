package com.skillmirror.candidate.dto;

import com.skillmirror.candidate.entity.CandidateCv;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CvResponse(UUID id, String fileName, Long sizeBytes, boolean active, OffsetDateTime uploadedAt) {
    public static CvResponse from(CandidateCv c) {
        return new CvResponse(c.getId(), c.getFileName(),
                c.getSizeBytes() == null ? 0L : c.getSizeBytes(),
                Boolean.TRUE.equals(c.getMain()), c.getUploadedAt());
    }
}