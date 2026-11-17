import '../models/recruiter_profile.dart';
import 'api_service.dart';

class RecruiterService {
  final _api = ApiService();

  Future<RecruiterProfile> getProfile() async =>
      RecruiterProfile.fromJson(await _api.get('/recruiter/profile'));

  Future<RecruiterProfile> saveProfile(Map<String, dynamic> data) async =>
      RecruiterProfile.fromJson(await _api.put('/recruiter/profile', data));

  Future<List<RefItem>> sectors() async =>
      (await _api.get('/reference/sectors') as List)
          .map((e) => RefItem.fromJson(e))
          .toList();

  Future<List<RefItem>> domains() async =>
      (await _api.get('/reference/recruitment-domains') as List)
          .map((e) => RefItem.fromJson(e))
          .toList();
}
