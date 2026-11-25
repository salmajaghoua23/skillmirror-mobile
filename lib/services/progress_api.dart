import 'api_service.dart';

class AnalysisProgress {
  final String status; // QUEUED, PROCESSING, DONE, FAILED, UNKNOWN
  final int percent;
  final String message;

  AnalysisProgress(this.status, this.percent, this.message);

  factory AnalysisProgress.fromJson(Map<String, dynamic> j) => AnalysisProgress(
    j['status'] as String? ?? 'UNKNOWN',
    (j['percent'] as num?)?.toInt() ?? 0,
    j['message'] as String? ?? '',
  );

  bool get isRunning => status == 'QUEUED' || status == 'PROCESSING';
}

class ProgressApi {
  final _api = ApiService();

  Future<AnalysisProgress> cv(String cvId) async => AnalysisProgress.fromJson(
      Map<String, dynamic>.from(
          await _api.get('/candidate/cvs/$cvId/analysis/progress') as Map));

  Future<AnalysisProgress> goal(String profileId) async =>
      AnalysisProgress.fromJson(Map<String, dynamic>.from(
          await _api.get('/progress/goal/$profileId') as Map));

  /// Lance l'analyse du métier visé seulement si elle n'existe pas encore.
  Future<AnalysisProgress> ensureGoal(String profileId) async =>
      AnalysisProgress.fromJson(Map<String, dynamic>.from(
          await _api.post('/progress/goal/$profileId/ensure', {}) as Map));

  /// Force une nouvelle analyse (bouton « actualiser »).
  Future<AnalysisProgress> refreshGoal(String profileId) async =>
      AnalysisProgress.fromJson(Map<String, dynamic>.from(
          await _api.post('/progress/goal/$profileId/refresh', {}) as Map));
}