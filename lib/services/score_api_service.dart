
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/score_models.dart';

class ScoreApiService {
  final String baseUrl;

  final Future<String?> Function() tokenProvider;

  ScoreApiService(this.baseUrl, this.tokenProvider);

  Future<Map<String, String>> get _h async {
    final token = await tokenProvider();

    if (token == null || token.isEmpty) {
      throw Exception('Utilisateur non authentifié');
    }

    return {
      'Authorization': 'Bearer $token',
      'Content-Type': 'application/json',
    };
  }

  // Charger le Dashboard sans relancer Ollama
  Future<DashboardDto> getDashboard(String profileId) async {
    final r = await http.get(
      Uri.parse('$baseUrl/score/$profileId/dashboard'),
      headers: await _h,
    );

    if (r.statusCode != 200) {
      throw Exception('Erreur ${r.statusCode}: ${r.body}');
    }

    return DashboardDto.fromJson(
      jsonDecode(utf8.decode(r.bodyBytes)),
    );
  }

  // Demander une nouvelle analyse Ollama
  Future<void> refresh(String profileId) async {
    final r = await http.post(
      Uri.parse('$baseUrl/score/$profileId/refresh'),
      headers: await _h,
    );

    if (r.statusCode != 200 && r.statusCode != 204) {
      throw Exception('Erreur ${r.statusCode}: ${r.body}');
    }
  }
}
