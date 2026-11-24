import '../models/assessment.dart';
import 'api_service.dart';

class AssessmentService {
  final _api = ApiService();

  Future<List<AvailableSkill>> available() async =>
      (await _api.get('/assessments/available') as List)
          .map((e) => AvailableSkill.fromJson(e as Map<String, dynamic>))
          .toList();

  Future<QuizSession> start(String skillId, String level, {int count = 5}) async =>
      QuizSession.fromJson(
        await _api.post('/assessments/start', {
          'skillId': skillId,
          'level': level,
          'count': count,
        }),
      );

  /// answers : questionId -> index de l'option choisie
  Future<AssessmentResult> submit(
      String assessmentId,
      Map<String, int> answers,
      List<QuizQuestion> questions,
      ) async => AssessmentResult.fromJson(
    await _api.post('/assessments/$assessmentId/submit', {
      'answers': [
        for (final q in questions)
          {'questionId': q.id, 'selectedIndex': answers[q.id]},
      ],
    }),
  );

  Future<AssessmentResult> result(String assessmentId) async =>
      AssessmentResult.fromJson(
        await _api.get('/assessments/$assessmentId/result'),
      );

  Future<List<HistoryItem>> history() async =>
      (await _api.get('/assessments/history') as List)
          .map((e) => HistoryItem.fromJson(e as Map<String, dynamic>))
          .toList();
}