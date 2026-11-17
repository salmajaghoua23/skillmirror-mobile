import 'package:flutter/material.dart';

import '../../services/auth_service.dart';
import '../../services/recruiter_service.dart';
import '../../widgets/recruiter_profile_form.dart';
import '../auth_gate.dart';

class RecruiterOnboardingScreen extends StatelessWidget {
  const RecruiterOnboardingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profil recruteur'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Se déconnecter',
            onPressed: () => AuthService().signOut(),
          ),
        ],
      ),
      body: RecruiterProfileForm(
        submitLabel: 'Terminer',
        onSubmit: (data) async {
          await RecruiterService().saveProfile(data);
          if (!context.mounted) return;
          Navigator.of(context).pushAndRemoveUntil(
            MaterialPageRoute(builder: (_) => const AuthGate()),
            (_) => false,
          );
        },
      ),
    );
  }
}
