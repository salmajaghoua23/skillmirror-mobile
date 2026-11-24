
import 'package:flutter/material.dart';

import '../models/score_models.dart';
import '../services/score_api_service.dart';

class ScoreProvider extends ChangeNotifier {
  final ScoreApiService api;

  ScoreProvider(this.api);

  DashboardDto? data;
  bool loading = false;
  String? error;

  // Chargement normal : utilise les données sauvegardées
  Future<void> load(String profileId) async {
    loading = true;
    error = null;
    notifyListeners();

    try {
      data = await api.getDashboard(profileId);
    } catch (e) {
      error = e.toString();
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  // Nouvelle analyse IA demandée par l'utilisateur
  Future<void> refreshAnalysis(String profileId) async {
    loading = true;
    error = null;
    notifyListeners();

    try {
      // 1. Appeler Ollama via le backend
      await api.refresh(profileId);

      // 2. Récupérer les nouveaux résultats
      data = await api.getDashboard(profileId);

    } catch (e) {
      error = e.toString();
      rethrow;
    } finally {
      loading = false;
      notifyListeners();
    }
  }
}
