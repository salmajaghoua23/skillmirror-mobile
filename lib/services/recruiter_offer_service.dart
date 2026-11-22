import '../models/job_offer.dart';
import 'api_service.dart';

class RecruiterOfferService {
  final _api = ApiService();

  // --- Offres ---
  Future<List<JobOffer>> offers({String? status}) async {
    final path = status == null
        ? '/recruiter/offers'
        : '/recruiter/offers?status=$status';
    return (await _api.get(path) as List)
        .map((e) => JobOffer.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<JobOffer> offer(String id) async =>
      JobOffer.fromJson(await _api.get('/recruiter/offers/$id'));

  Future<JobOffer> create(Map<String, dynamic> data) async =>
      JobOffer.fromJson(await _api.post('/recruiter/offers', data));

  Future<JobOffer> update(String id, Map<String, dynamic> data) async =>
      JobOffer.fromJson(await _api.put('/recruiter/offers/$id', data));

  Future<JobOffer> setStatus(String id, String status) async =>
      JobOffer.fromJson(
        await _api.put('/recruiter/offers/$id/status', {'status': status}),
      );

  Future<void> delete(String id) async {
    await _api.delete('/recruiter/offers/$id');
  }

  // --- Candidatures ---
  Future<List<JobApplication>> applications(String offerId) async =>
      (await _api.get('/recruiter/offers/$offerId/applications') as List)
          .map((e) => JobApplication.fromJson(e as Map<String, dynamic>))
          .toList();

  Future<JobApplication> setApplicationStatus(String id, String status) async =>
      JobApplication.fromJson(
        await _api.put('/recruiter/applications/$id/status', {'status': status}),
      );

  // --- Candidats ---
  Future<List<CandidateSummary>> matches(String offerId) async =>
      (await _api.get('/recruiter/offers/$offerId/matches') as List)
          .map((e) => CandidateSummary.fromJson(e as Map<String, dynamic>))
          .toList();

  Future<CandidateDetail> candidate(String profileId) async =>
      CandidateDetail.fromJson(await _api.get('/recruiter/candidates/$profileId'));

  // --- Dashboard ---
  Future<RecruiterDashboard> dashboard() async =>
      RecruiterDashboard.fromJson(await _api.get('/recruiter/dashboard'));
}
