import 'package:flutter/material.dart';
import '../../services/auth_service.dart';
import '../settings_screen.dart';

class CandidateHomeScreen extends StatelessWidget {
  const CandidateHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SkillMirror – Candidat'),
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
      body: const Center(child: Text('Bienvenue, candidat !')),
    );
  }
}