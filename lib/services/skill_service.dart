import '../models/skill_reference.dart';
import 'api_service.dart';

class SkillService {
  final _api = ApiService();

  Future<List<SkillDomain>> domains() async =>
      (await _api.get('/skills/domains') as List)
          .map((e) => SkillDomain.fromJson(e as Map<String, dynamic>))
          .toList();

  Future<List<JobRoleItem>> roles() async =>
      (await _api.get('/skills/roles') as List)
          .map((e) => JobRoleItem.fromJson(e as Map<String, dynamic>))
          .toList();

  Future<JobRoleDetail> role(String id) async =>
      JobRoleDetail.fromJson(await _api.get('/skills/roles/$id'));
}