import 'package:flutter/material.dart';

import '../core/app_colors.dart';
import '../core/app_theme.dart';

class SkillMirrorLogo extends StatelessWidget {
  const SkillMirrorLogo({super.key});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.bar_chart_rounded, color: AppColors.primary, size: 28),
        const SizedBox(width: 8),
        Text(
          'SkillMirror',
          style: TextStyle(
            color: context.palette.textPrimary,
            fontSize: 18,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}