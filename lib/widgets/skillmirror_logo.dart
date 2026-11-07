import 'package:flutter/material.dart';

import '../core/app_colors.dart';

class SkillMirrorLogo extends StatelessWidget {
  const SkillMirrorLogo({super.key});

  @override
  Widget build(BuildContext context) {
    return const Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.bar_chart_rounded, color: AppColors.primary, size: 28),
        SizedBox(width: 8),
        Text(
          'SkillMirror',
          style: TextStyle(
            color: AppColors.textPrimary,
            fontSize: 18,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}
