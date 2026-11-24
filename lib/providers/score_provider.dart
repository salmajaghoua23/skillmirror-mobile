import 'package:flutter/material.dart';
import '../models/score_models.dart';
import '../services/score_api_service.dart';

class ScoreProvider extends ChangeNotifier {
  final ScoreApiService api;
  ScoreProvider(this.api);

  DashboardDto? data;
  bool loading = false;
  String? error;

  Future<void> load(String profileId) async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      data = await api.getDashboard(profileId);
    } catch (e) {
      error = e.toString();
    }
    loading = false;
    notifyListeners();
  }
}