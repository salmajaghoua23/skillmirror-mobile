import 'package:flutter/material.dart';

import '../core/app_theme.dart';
import '../core/theme_controller.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = ThemeController.instance;
    return Scaffold(
      appBar: AppBar(title: const Text('Apparence')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            'Thème de l\'application',
            style: TextStyle(
              color: context.palette.textPrimary,
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '« Système » suit le réglage de votre téléphone.',
            style: TextStyle(color: context.palette.textSecondary, fontSize: 13),
          ),
          const SizedBox(height: 18),
          ListenableBuilder(
            listenable: controller,
            builder: (context, _) => SizedBox(
              width: double.infinity,
              child: SegmentedButton<ThemeMode>(
                showSelectedIcon: false,
                segments: const [
                  ButtonSegment(
                    value: ThemeMode.light,
                    icon: Icon(Icons.light_mode_outlined),
                    label: Text('Clair'),
                  ),
                  ButtonSegment(
                    value: ThemeMode.dark,
                    icon: Icon(Icons.dark_mode_outlined),
                    label: Text('Sombre'),
                  ),
                  ButtonSegment(
                    value: ThemeMode.system,
                    icon: Icon(Icons.phone_android_outlined),
                    label: Text('Système'),
                  ),
                ],
                selected: {controller.mode},
                onSelectionChanged: (s) => controller.setMode(s.first),
              ),
            ),
          ),
        ],
      ),
    );
  }
}