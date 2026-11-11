import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import '../services/auth_service.dart';
import '../services/profile_service.dart';
import 'home/candidate_home_screen.dart';
import 'home/recruiter_home_screen.dart';
import 'onboarding/candidate_onboarding_screen.dart';
import 'onboarding/recruiter_onboarding_screen.dart';
import 'role_selection_screen.dart';

class AuthGate extends StatelessWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<User?>(
      stream: FirebaseAuth.instance.authStateChanges(),
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) return _loading();
        if (snap.data == null) return const RoleSelectionScreen();

        return FutureBuilder<Map<String, dynamic>?>(
          future: ProfileService().getAccountOrCreate(),
          builder: (context, acc) {
            if (acc.connectionState != ConnectionState.done) return _loading();
            if (acc.hasError) {
              return Scaffold(
                body: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('Erreur : ${acc.error}'),
                        const SizedBox(height: 16),
                        ElevatedButton(
                            onPressed: () => AuthService().signOut(),
                            child: const Text('Se déconnecter')),
                      ],
                    ),
                  ),
                ),
              );
            }
            final account = acc.data;
            if (account == null) return const RoleSelectionScreen();

            final isCandidate = account['role'] == 'candidate';
            if (account['onboarding_done'] != true) {
              return isCandidate
                  ? const CandidateOnboardingScreen()
                  : const RecruiterOnboardingScreen();
            }
            return isCandidate
                ? const CandidateHomeScreen()
                : const RecruiterHomeScreen();
          },
        );
      },
    );
  }

  Widget _loading() =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}