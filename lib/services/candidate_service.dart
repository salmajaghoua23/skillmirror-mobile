import '../models/candidate_profile.dart';
import 'api_service.dart';

class MeResponse {
  final String role;
  final bool onboardingDone;
  final String fullName;
  MeResponse(this.role, this.onboardingDone, this.fullName);
  factory MeResponse.fromJson(Map<String, dynamic> j) =>
      MeResponse(j['role'], j['onboardingDone'] ?? false, j['fullName'] ?? '');
}

class CandidateService {
  final _api = ApiService();

  /// null si l'utilisateur n'a pas encore choisi de rôle (404)
  Future<MeResponse?> getMe() async {
    try {
      return MeResponse.fromJson(await _api.get('/me'));
    } on ApiException catch (e) {
      if (e.status == 404) return null;
      rethrow;
    }
  }

  /// role : 'candidate' ou 'recruiter' (crée la ligne profiles)
  Future<MeResponse> register(String role) async =>
      MeResponse.fromJson(await _api.post('/auth/register', {'role': role}));

  Future<CandidateProfile> getCandidateProfile() async =>
      CandidateProfile.fromJson(await _api.get('/candidate/profile'));

  /// Formulaire d'inscription complet (infos perso + préférences)
  Future<void> submitOnboarding(CandidateProfile p) async => _api.post(
    '/candidate/onboarding',
    {'profile': p.profileJson(), 'preferences': p.preferencesJson()},
  );

  Future<void> updateCandidateProfile(CandidateProfile p) async =>
      _api.put('/candidate/profile', p.profileJson());

  Future<void> savePreferences(CandidateProfile p) async =>
      _api.put('/candidate/preferences', p.preferencesJson());
}
