import 'package:flutter/material.dart';
import '../../services/auth_service.dart';
import '../settings_screen.dart';
import '../candidate/profile_screen.dart';
import '../candidate/cv_screen.dart';
import '../candidate/cv_insights_card.dart';
import '../assessment/assessment_home_screen.dart';
class CandidateHomeScreen extends StatefulWidget {
  const CandidateHomeScreen({super.key});

  @override
  State<CandidateHomeScreen> createState() => _CandidateHomeScreenState();
}

class _CandidateHomeScreenState extends State<CandidateHomeScreen> {
  int _refresh = 0; // change la clé de la carte pour la recharger

  Future<void> _open(Widget screen) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
    if (mounted) setState(() => _refresh++);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SkillMirror – Candidat'),
        actions: [
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            tooltip: 'Mon profil',
            onPressed: () => _open(const ProfileScreen()),
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Apparence',
            onPressed: () => _open(const SettingsScreen()),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => AuthService().signOut(),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 24),
        children: [
          Text('Bonjour 👋',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 4),
          Text('Voici le résumé de ton profil professionnel.',
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          const SizedBox(height: 20),
          CvInsightsCard(key: ValueKey(_refresh)),
          const SizedBox(height: 14),
          OutlinedButton.icon(
            icon: const Icon(Icons.description_outlined),
            label: const Text('Gérer mes CV'),
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            onPressed: () => _open(const CvScreen()),
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            icon: const Icon(Icons.quiz_outlined),
            label: const Text('Tests de compétences'),
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            onPressed: () => _open(const AssessmentHomeScreen()),
          ),
        ],
      ),
    );
  }
}