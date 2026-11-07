import 'package:flutter/material.dart';

import '../core/app_colors.dart';
import '../models/user_role.dart';
import '../widgets/primary_button.dart';
import '../widgets/role_card.dart';
import '../widgets/skillmirror_logo.dart';

class RoleSelectionScreen extends StatefulWidget {
  const RoleSelectionScreen({super.key});

  @override
  State<RoleSelectionScreen> createState() => _RoleSelectionScreenState();
}

class _RoleSelectionScreenState extends State<RoleSelectionScreen> {
  UserRole _selected = UserRole.jobSeeker;

  void _onContinue() {
    // TODO: naviguer vers l'écran suivant avec _selected
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Profil choisi : ${_selected.title}')),
    );
  }

  void _onLogin() {
    // TODO: naviguer vers l'écran de connexion
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SkillMirrorLogo(),
              const SizedBox(height: 28),
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Le bon profil, au bon\nrecruteur',
                        style: TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 28,
                          fontWeight: FontWeight.w800,
                          height: 1.2,
                        ),
                      ),
                      const SizedBox(height: 10),
                      const Text(
                        'Choisissez comment vous utilisez SkillMirror.',
                        style: TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 22),
                      for (final role in UserRole.values) ...[
                        RoleCard(
                          role: role,
                          selected: role == _selected,
                          onTap: () => setState(() => _selected = role),
                        ),
                        const SizedBox(height: 12),
                      ],
                    ],
                  ),
                ),
              ),
              PrimaryButton(label: 'Continuer', onPressed: _onContinue),
              const SizedBox(height: 14),
              Center(
                child: GestureDetector(
                  onTap: _onLogin,
                  child: const Text.rich(
                    TextSpan(
                      text: 'Déjà un compte ? ',
                      style: TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 12.5,
                      ),
                      children: [
                        TextSpan(
                          text: 'Se connecter',
                          style: TextStyle(
                            color: AppColors.textPrimary,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
