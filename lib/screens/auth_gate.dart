import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../services/auth_service.dart';
import '../services/profile_service.dart';
import '../services/candidate_service.dart';
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
        if (snap.connectionState == ConnectionState.waiting) {
          return _loading();
        }

        if (snap.data == null) {
          return const RoleSelectionScreen();
        }

        return FutureBuilder(
          future: CandidateService().getMe(),
          builder: (context, acc) {
            if (acc.connectionState != ConnectionState.done) {
              return _loading();
            }

            if (acc.hasError) {
              return Scaffold(
                body: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text('Erreur : ${acc.error}'),
                      ElevatedButton(
                        onPressed: () => AuthService().signOut(),
                        child: const Text('Se déconnecter'),
                      ),
                    ],
                  ),
                ),
              );
            }

            final me = acc.data;

            if (me == null) {
              return const RoleSelectionScreen();
            }

            if (!me.onboardingDone) {
              return me.role == 'candidate'
                  ? const CandidateOnboardingScreen()
                  : const RecruiterOnboardingScreen();
            }

            return me.role == 'candidate'
                ? const CandidateHomeScreen()
                : const RecruiterHomeScreen();
          },
        );
      },
    );
  }

  Widget _loading() {
    return const Scaffold(body: Center(child: CircularProgressIndicator()));
  }
}
