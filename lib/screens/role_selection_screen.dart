import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../core/app_colors.dart';
import '../models/user_role.dart';
import '../services/auth_service.dart';
import '../services/profile_service.dart';
import '../widgets/primary_button.dart';
import '../widgets/role_card.dart';
import '../widgets/skillmirror_logo.dart';
import 'auth_gate.dart';

class RoleSelectionScreen extends StatefulWidget {
  const RoleSelectionScreen({super.key});

  @override
  State<RoleSelectionScreen> createState() => _RoleSelectionScreenState();
}

class _RoleSelectionScreenState extends State<RoleSelectionScreen> {
  UserRole _selected = UserRole.jobSeeker;
  bool _loading = false;

  // Valeur enregistrée dans la colonne profiles.role
  String get _roleValue =>
      _selected == UserRole.jobSeeker ? 'candidate' : 'recruiter';

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  /// Inscription : Google + création du compte avec le rôle choisi.
  Future<void> _onContinue() async {
    if (_loading) return;
    setState(() => _loading = true);
    try {
      // Cas rare : déjà connecté à Firebase mais sans compte Supabase
      if (FirebaseAuth.instance.currentUser != null) {
        await ProfileService().createAccount(_roleValue);
        if (!mounted) return;
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const AuthGate()),
              (_) => false,
        );
        return;
      }

      // On mémorise le rôle : AuthGate créera le compte juste après la connexion
      ProfileService.pendingRole = _roleValue;
      final user = await AuthService().signInWithGoogle();
      if (user == null) {
        ProfileService.pendingRole = null; // connexion annulée
      }
      // Pas de navigation ici : AuthGate réagit tout seul à la connexion.
    } catch (e) {
      ProfileService.pendingRole = null;
      _showError('Connexion impossible : $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Connexion d'un utilisateur qui a déjà un compte.
  Future<void> _onLogin() async {
    if (_loading) return;
    setState(() => _loading = true);
    try {
      ProfileService.pendingRole = null; // on ne crée rien
      await AuthService().signInWithGoogle();
      // AuthGate ouvre le bon écran selon le compte existant.
    } catch (e) {
      _showError('Connexion impossible : $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
              if (_loading)
                const Padding(
                  padding: EdgeInsets.only(bottom: 12),
                  child: LinearProgressIndicator(),
                ),
              PrimaryButton(label: 'Continuer avec Google', onPressed: _onContinue),
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