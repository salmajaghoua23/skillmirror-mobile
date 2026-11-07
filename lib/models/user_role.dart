import 'package:flutter/material.dart';

import '../core/app_colors.dart';

enum UserRole {
  jobSeeker(
    title: 'Chercheur d\'emploi',
    description: 'Déposez vos CV et recevez des offres adaptées.',
    icon: Icons.person_outline_rounded,
    color: AppColors.jobSeeker,
  ),
  recruiter(
    title: 'Recruteur',
    description: 'Publiez vos offres et recevez des candidats qualifiés.',
    icon: Icons.business_center_outlined,
    color: AppColors.recruiter,
  );

  const UserRole({
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
  });
  final String title;
  final String description;
  final IconData icon;
  final Color color;
}
