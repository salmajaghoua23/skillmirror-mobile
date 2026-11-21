import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Couleurs qui changent selon le mode clair / sombre.
/// Les couleurs de marque (AppColors.primary, onPrimary, jobSeeker, recruiter)
/// restent identiques dans les deux modes.
@immutable
class AppPalette extends ThemeExtension<AppPalette> {
  const AppPalette({
    required this.background,
    required this.card,
    required this.cardBorder,
    required this.textPrimary,
    required this.textSecondary,
  });

  final Color background;
  final Color card;
  final Color cardBorder;
  final Color textPrimary;
  final Color textSecondary;

  static const dark = AppPalette(
    background: Color(0xFF0B1220),
    card: Color(0xFF131C2E),
    cardBorder: Color(0xFF22304A),
    textPrimary: Colors.white,
    textSecondary: Color(0xFF93A1B8),
  );

  static const light = AppPalette(
    background: Color(0xFFF5F7FB),
    card: Colors.white,
    cardBorder: Color(0xFFD9E0EC),
    textPrimary: Color(0xFF0B1220),
    textSecondary: Color(0xFF5B6B84),
  );

  @override
  AppPalette copyWith({
    Color? background,
    Color? card,
    Color? cardBorder,
    Color? textPrimary,
    Color? textSecondary,
  }) =>
      AppPalette(
        background: background ?? this.background,
        card: card ?? this.card,
        cardBorder: cardBorder ?? this.cardBorder,
        textPrimary: textPrimary ?? this.textPrimary,
        textSecondary: textSecondary ?? this.textSecondary,
      );

  @override
  AppPalette lerp(ThemeExtension<AppPalette>? other, double t) {
    if (other is! AppPalette) return this;
    return AppPalette(
      background: Color.lerp(background, other.background, t)!,
      card: Color.lerp(card, other.card, t)!,
      cardBorder: Color.lerp(cardBorder, other.cardBorder, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
    );
  }
}

extension AppPaletteContext on BuildContext {
  /// Usage : context.palette.background, context.palette.textPrimary...
  AppPalette get palette => Theme.of(this).extension<AppPalette>()!;
}

class AppTheme {
  AppTheme._();

  static ThemeData get dark => _build(Brightness.dark, AppPalette.dark);
  static ThemeData get light => _build(Brightness.light, AppPalette.light);

  static ThemeData _build(Brightness brightness, AppPalette p) {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.primary,
      brightness: brightness,
    ).copyWith(
      primary: AppColors.primary,
      onPrimary: AppColors.onPrimary,
      surface: p.card,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: p.background,
      appBarTheme: AppBarTheme(
        backgroundColor: p.background,
        foregroundColor: p.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0,
      ),
      extensions: [p],
    );
  }
}