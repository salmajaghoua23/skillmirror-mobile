import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Garde le mode choisi (clair / sombre / système) et le sauvegarde
/// sur le téléphone pour le retrouver au prochain lancement.
class ThemeController extends ChangeNotifier {
  ThemeController._();
  static final ThemeController instance = ThemeController._();

  static const _key = 'theme_mode';

  // Sombre par défaut : c'était l'apparence actuelle de l'application.
  ThemeMode _mode = ThemeMode.dark;
  ThemeMode get mode => _mode;

  /// À appeler dans main() avant runApp.
  Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(_key);
      _mode = ThemeMode.values.firstWhere(
            (m) => m.name == saved,
        orElse: () => ThemeMode.dark,
      );
    } catch (_) {
      // Lecture impossible : on garde le mode par défaut.
    }
  }

  Future<void> setMode(ThemeMode mode) async {
    if (mode == _mode) return;
    _mode = mode;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_key, mode.name);
    } catch (_) {
      // Sauvegarde impossible : le choix reste valable pour cette session.
    }
  }
}