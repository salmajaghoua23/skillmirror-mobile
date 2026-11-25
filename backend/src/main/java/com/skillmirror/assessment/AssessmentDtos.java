package com.skillmirror.assessment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {

    private AssessmentDtos() {}

    // ---------- Entrées ----------

    public record StartRequest(
            @NotNull UUID skillId,
            @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Niveau invalide")
            String level,
            @Min(1) @Max(10) Integer count) {}

    /** selectedIndex = null si la question n'a pas été répondue. */
    public record AnswerIn(@NotNull UUID questionId, Integer selectedIndex) {}

    public record SubmitRequest(@NotNull List<@Valid AnswerIn> answers) {}

    // ---------- Sorties ----------

    /** Question envoyée au candidat : SANS la bonne réponse. */
    public record QuestionOut(UUID id, String text, List<String> options) {}

    public record StartResponse(UUID assessmentId, String skill, String level,
                                List<QuestionOut> questions) {}

    /** État d'un niveau pour un candidat : verrouillé, validé, meilleur score... */
    public record LevelStatus(String level, boolean locked, boolean hasQuestions,
                              Double bestScore, boolean passed, int questionCount) {}

    public record AvailableSkill(UUID skillId, String name, String category,
                                 boolean recommended, List<LevelStatus> levels) {}

    public record GenerateResponse(int generated, int passagesUsed, List<String> questions) {}

    /** Correction d'une question (stockée dans assessments.results). */
    public record ResultQuestion(UUID questionId, String text, List<String> options,
                                 Integer selectedIndex, int correctIndex,
                                 boolean correct, String explanation) {}

    /** nextLevel = niveau suivant (null si c'est le dernier). passMark = seuil de réussite en %. */
    public record ResultResponse(UUID assessmentId, UUID skillId, String skill, String level,
                                 double score, int correctCount, int total, boolean passed,
                                 double passMark, String nextLevel, String status,
                                 Instant finishedAt, List<ResultQuestion> questions) {}

    public record HistoryItem(UUID id, UUID skillId, String skill, String level, String status,
                              Double score, Integer total, Instant createdAt, Instant finishedAt) {}
}