package com.skillmirror.scoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ScoringDtos {
    public record ScoreDto(double global, double cv, double form, double assessment, Instant date) {}
    public record SkillLevelDto(UUID skillId, String skillName, int level, String label, int required) {}
    public record MissingSkillDto(UUID skillId, String skillName, int requiredLevel, int currentLevel, String gap) {}
    public record GoalComparisonDto(String targetRole, double matchPercent,
                                    List<SkillLevelDto> strengths, List<MissingSkillDto> missing) {}
    public record RecommendationDto(UUID id, String type, String priority, String message, UUID skillId) {}
    public record DashboardDto(ScoreDto score, List<ScoreDto> history,
                               GoalComparisonDto goal, List<RecommendationDto> recommendations) {}
}