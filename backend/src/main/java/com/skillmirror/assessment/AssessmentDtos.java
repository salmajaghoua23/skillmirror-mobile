package com.skillmirror.assessment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {

    private AssessmentDtos() {}

    public record StartRequest(
            @NotNull UUID skillId,
            @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Niveau invalide")
            String level,
            @Min(1) @Max(10) Integer count) {}

    /** Question envoyée au candidat : SANS la bonne réponse. */
    public record QuestionOut(UUID id, String text, List<String> options) {}

    public record StartResponse(UUID assessmentId, String skill, String level,
                                List<QuestionOut> questions) {}

    public record AvailableSkill(UUID skillId, String name, String category, long questionCount) {}

    public record GenerateResponse(int generated, int passagesUsed, List<String> questions) {}
}