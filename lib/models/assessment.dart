class AssessmentLabels {
  AssessmentLabels._();

  static const levels = {
    'BEGINNER': 'Débutant',
    'INTERMEDIATE': 'Intermédiaire',
    'ADVANCED': 'Avancé',
  };

  static const levelHints = {
    'BEGINNER': 'Définitions et notions de base',
    'INTERMEDIATE': 'Mise en pratique et comparaisons',
    'ADVANCED': 'Cas pratiques et bonnes pratiques',
  };
}

class AvailableSkill {
  final String skillId, name;
  final String? category;
  final int questionCount;

  AvailableSkill({
    required this.skillId,
    required this.name,
    this.category,
    required this.questionCount,
  });

  factory AvailableSkill.fromJson(Map<String, dynamic> j) => AvailableSkill(
    skillId: j['skillId'] as String,
    name: j['name'] ?? '',
    category: j['category'],
    questionCount: (j['questionCount'] as num? ?? 0).toInt(),
  );
}

class QuizQuestion {
  final String id, text;
  final List<String> options;

  QuizQuestion({required this.id, required this.text, required this.options});

  factory QuizQuestion.fromJson(Map<String, dynamic> j) => QuizQuestion(
    id: j['id'] as String,
    text: j['text'] ?? '',
    options: (j['options'] as List? ?? const []).map((e) => e.toString()).toList(),
  );
}

class QuizSession {
  final String assessmentId, skill, level;
  final List<QuizQuestion> questions;

  QuizSession({
    required this.assessmentId,
    required this.skill,
    required this.level,
    required this.questions,
  });

  factory QuizSession.fromJson(Map<String, dynamic> j) => QuizSession(
    assessmentId: j['assessmentId'] as String,
    skill: j['skill'] ?? '',
    level: j['level'] ?? '',
    questions: (j['questions'] as List? ?? const [])
        .map((e) => QuizQuestion.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

class ResultQuestion {
  final String text;
  final List<String> options;
  final int? selectedIndex;
  final int correctIndex;
  final bool correct;
  final String? explanation;

  ResultQuestion({
    required this.text,
    required this.options,
    required this.selectedIndex,
    required this.correctIndex,
    required this.correct,
    this.explanation,
  });

  factory ResultQuestion.fromJson(Map<String, dynamic> j) => ResultQuestion(
    text: j['text'] ?? '',
    options: (j['options'] as List? ?? const []).map((e) => e.toString()).toList(),
    selectedIndex: (j['selectedIndex'] as num?)?.toInt(),
    correctIndex: (j['correctIndex'] as num? ?? 0).toInt(),
    correct: j['correct'] == true,
    explanation: j['explanation'],
  );
}

class AssessmentResult {
  final String assessmentId, skill, level;
  final double score;
  final int correctCount, total;
  final bool passed;
  final List<ResultQuestion> questions;

  AssessmentResult({
    required this.assessmentId,
    required this.skill,
    required this.level,
    required this.score,
    required this.correctCount,
    required this.total,
    required this.passed,
    required this.questions,
  });

  factory AssessmentResult.fromJson(Map<String, dynamic> j) => AssessmentResult(
    assessmentId: j['assessmentId'] as String,
    skill: j['skill'] ?? '',
    level: j['level'] ?? '',
    score: (j['score'] as num? ?? 0).toDouble(),
    correctCount: (j['correctCount'] as num? ?? 0).toInt(),
    total: (j['total'] as num? ?? 0).toInt(),
    passed: j['passed'] == true,
    questions: (j['questions'] as List? ?? const [])
        .map((e) => ResultQuestion.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

class HistoryItem {
  final String id, skill, level, status;
  final double? score;
  final int? total;
  final String? createdAt;

  HistoryItem({
    required this.id,
    required this.skill,
    required this.level,
    required this.status,
    this.score,
    this.total,
    this.createdAt,
  });

  factory HistoryItem.fromJson(Map<String, dynamic> j) => HistoryItem(
    id: j['id'] as String,
    skill: j['skill'] ?? '',
    level: j['level'] ?? '',
    status: j['status'] ?? '',
    score: (j['score'] as num?)?.toDouble(),
    total: (j['total'] as num?)?.toInt(),
    createdAt: j['createdAt'],
  );
}