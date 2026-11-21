import 'package:flutter/material.dart';
import '../../services/auth_service.dart';
import '../recruiter/recruiter_profile_screen.dart';
import '../settings_screen.dart';

class RecruiterHomeScreen extends StatelessWidget {
  const RecruiterHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SkillMirror – Recruteur'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Apparence',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
          IconButton(
              icon: const Icon(Icons.logout),
              onPressed: () => AuthService().signOut()),
        ],
      ),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Bienvenue, Recruteur !'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => const RecruiterProfileScreen())),
              child: const Text('Mon profil'),
            ),
          ],
        ),
      ),
    );
  }
}