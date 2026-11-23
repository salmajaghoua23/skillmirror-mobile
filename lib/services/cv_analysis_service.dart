import 'api_service.dart';

class CvAnalysisService {
  final _api = ApiService();
  static const _base = '/candidate/cvs';

  /// Renvoie null s'il n'y a aucun CV (réponse vide).
  Future<Map<String, dynamic>?> latest() async {
    final data = await _api.get('$_base/analysis/latest');
    if (data == null || (data is String && data.isEmpty)) return null;
    return Map<String, dynamic>.from(data as Map);
  }

  Future<Map<String, dynamic>> run(String cvId) async =>
      Map<String, dynamic>.from(
        await _api.post(
          '$_base/$cvId/analysis/run',
          {},
          timeout: const Duration(minutes: 12),
        ) as Map,
      );

  Future<Map<String, dynamic>> get(String cvId) async =>
      Map<String, dynamic>.from(await _api.get('$_base/$cvId/analysis') as Map);

  Future<Map<String, dynamic>> update(String cvId, Map<String, dynamic> data) async =>
      Map<String, dynamic>.from(await _api.put('$_base/$cvId/analysis', data) as Map);

  Future<Map<String, dynamic>> confirm(String cvId) async =>
      Map<String, dynamic>.from(await _api.post('$_base/$cvId/analysis/confirm', {}) as Map);
}