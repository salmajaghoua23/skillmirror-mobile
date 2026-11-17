import '../models/candidate_cv.dart';
import 'api_service.dart';

class CandidateCvService {
  final _api = ApiService();

  Future<List<CandidateCv>> list() async {
    final data = await _api.get('/candidate/cvs');
    return (data as List).map((e) => CandidateCv.fromJson(e)).toList();
  }

  Future<CandidateCv> upload(String filePath, String fileName) async =>
      CandidateCv.fromJson(
        await _api.uploadFile('/candidate/cvs', filePath, fileName),
      );

  Future<void> setActive(String id) async =>
      _api.put('/candidate/cvs/$id/active', {});

  Future<void> delete(String id) async => _api.delete('/candidate/cvs/$id');
}
