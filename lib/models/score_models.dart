class ScoreDto {
  final double global, cv, form, assessment;
  final DateTime date;
  ScoreDto.fromJson(Map<String, dynamic> j)
      : global = (j['global'] ?? 0).toDouble(),
        cv = (j['cv'] ?? 0).toDouble(),
        form = (j['form'] ?? 0).toDouble(),
        assessment = (j['assessment'] ?? 0).toDouble(),
        date = DateTime.parse(j['date']);
}

class MissingSkill {
  final String skillId, skillName, gap;
  final int requiredLevel, currentLevel;
  MissingSkill.fromJson(Map<String, dynamic> j)
      : skillId = j['skillId'],
        skillName = j['skillName'],
        gap = j['gap'],
        requiredLevel = j['requiredLevel'],
        currentLevel = j['currentLevel'];
}

class RecommendationDto {
  final String id, type, priority, message;
  RecommendationDto.fromJson(Map<String, dynamic> j)
      : id = j['id'],
        type = j['type'],
        priority = j['priority'],
        message = j['message'];
}

class DashboardDto {
  final ScoreDto score;
  final List<ScoreDto> history;
  final String targetRole;
  final double matchPercent;
  final List<MissingSkill> missing;
  final List<RecommendationDto> recommendations;

  DashboardDto.fromJson(Map<String, dynamic> j)
      : score = ScoreDto.fromJson(j['score']),
        history = (j['history'] as List).map((e) => ScoreDto.fromJson(e)).toList(),
        targetRole = j['goal']['targetRole'],
        matchPercent = (j['goal']['matchPercent'] ?? 0).toDouble(),
        missing = (j['goal']['missing'] as List).map((e) => MissingSkill.fromJson(e)).toList(),
        recommendations = (j['recommendations'] as List)
            .map((e) => RecommendationDto.fromJson(e))
            .toList();
}