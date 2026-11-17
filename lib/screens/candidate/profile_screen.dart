import 'package:flutter/material.dart';

import '../../models/candidate_profile.dart';
import '../../services/candidate_service.dart';
import 'cv_screen.dart';
import 'edit_profile_screen.dart';
import 'preferences_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late Future<CandidateProfile> _future;

  @override
  void initState() {
    super.initState();
    _future = CandidateService().getCandidateProfile();
  }

  void _reload() {
    setState(() => _future = CandidateService().getCandidateProfile());
  }

  Future<void> _open(Widget screen) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => screen),
    );
    if (changed == true) _reload();
  }

  String _genderLabel(String? g) => switch (g) {
    'M' => 'Masculin',
    'F' => 'Féminin',
    'NOT_SPECIFIED' => 'Non précisé',
    _ => '-',
  };

  Widget _row(String label, String? value) => ListTile(
    dense: true,
    title: Text(
      label,
      style: const TextStyle(fontSize: 12, color: Colors.grey),
    ),
    subtitle: Text(
      (value == null || value.isEmpty) ? '-' : value,
      style: const TextStyle(fontSize: 16),
    ),
  );

  Widget _title(String text) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16),
    child: Text(text, style: const TextStyle(fontWeight: FontWeight.bold)),
  );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mon profil')),
      body: FutureBuilder<CandidateProfile>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError || !snap.hasData) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Impossible de charger le profil'),
                  TextButton(
                    onPressed: _reload,
                    child: const Text('Réessayer'),
                  ),
                ],
              ),
            );
          }
          final p = snap.data!;
          return ListView(
            children: [
              const SizedBox(height: 16),
              const Center(
                child: CircleAvatar(
                  radius: 40,
                  child: Icon(Icons.person, size: 40),
                ),
              ),
              const SizedBox(height: 8),
              Center(
                child: Text(
                  p.fullName,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              Center(
                child: Text(
                  p.email ?? '',
                  style: const TextStyle(color: Colors.grey),
                ),
              ),
              const SizedBox(height: 16),

              _title('Informations personnelles'),
              _row('Âge', p.age?.toString()),
              _row('Sexe', _genderLabel(p.gender)),
              _row('Pays', p.country),
              _row('Région', p.region),
              _row("Domaine d'études", p.studyField),
              _row("Niveau d'études", p.educationLevel),
              _row('Diplôme', p.diploma),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.edit),
                  label: const Text('Modifier mon profil'),
                  onPressed: () => _open(EditProfileScreen(profile: p)),
                ),
              ),

              const Divider(height: 32),
              _title('Préférences professionnelles'),
              _row('Secteur', p.sector),
              _row('Offre souhaitée', p.targetOfferDescription),
              _row('Disponibilité', p.availability),
              _row('Prêt à me déplacer', p.mobility ? 'Oui' : 'Non'),
              _row("Langue de l'entretien", p.interviewLanguage),
              _row("Centres d'intérêt", p.interests.join(', ')),
              _row("Objectif d'entraînement", p.trainingObjective),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.tune),
                  label: const Text('Modifier mes préférences'),
                  onPressed: () => _open(PreferencesScreen(profile: p)),
                ),
              ),

              const Divider(height: 32),
              _title('Mes CV'),
              Padding(
                padding: const EdgeInsets.all(16),
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.description),
                  label: const Text('Gérer mes CV'),
                  onPressed: () => _open(const CvScreen()),
                ),
              ),
              const SizedBox(height: 16),
            ],
          );
        },
      ),
    );
  }
}
