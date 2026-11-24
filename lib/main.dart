
import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:skill_mirror/core/app_theme.dart';
import 'package:skill_mirror/core/theme_controller.dart';
import 'package:skill_mirror/screens/auth_gate.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:provider/provider.dart';
import 'package:skill_mirror/config/api_config.dart';
import 'providers/score_provider.dart';
import 'services/score_api_service.dart';

import 'firebase_options.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await ThemeController.instance.load(); // mode clair/sombre sauvegardé

  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  await Supabase.initialize(
    url: 'https://bvqpuqdszrhnfwmxvsul.supabase.co',
    publishableKey: 'sb_publishable_YGYpgEjKqMI1x1UHKqCYOg_q07eeJLd',

    // Utiliser le jeton de l'utilisateur Firebase
    accessToken: () async {
      final user = FirebaseAuth.instance.currentUser;

      if (user == null) {
        return null;
      }

      return await user.getIdToken();
    },
  );

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: ThemeController.instance,
      builder: (context, _) => MultiProvider(
        providers: [
          ChangeNotifierProvider(
            create: (_) => ScoreProvider(
              ScoreApiService(
                ApiConfig.baseUrl,
                    () async {
                  final user = FirebaseAuth.instance.currentUser;

                  if (user == null) {
                    return null;
                  }

                  return await user.getIdToken();
                },
              ),
            ),
          ),
        ],
        child: MaterialApp(
          title: 'SkillMirror',
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light,
          darkTheme: AppTheme.dark,
          themeMode: ThemeController.instance.mode,
          home: const AuthGate(),
        ),
      ),
    );
  }
}
