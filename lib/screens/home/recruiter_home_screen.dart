import 'package:flutter/material.dart';

import '../../services/auth_service.dart';

class RecruiterHomeScreen extends StatelessWidget {
  const RecruiterHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SkillMirror – Recruteur'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => AuthService().signOut(),
          ),
        ],
      ),
      body: const Center(child: Text('Bienvenue, Recruteur!')),
    );
  }
}
