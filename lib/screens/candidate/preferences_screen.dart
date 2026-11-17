import 'package:flutter/material.dart';

import '../../models/candidate_profile.dart';
import '../../services/candidate_service.dart';

class PreferencesScreen extends StatefulWidget {
  final CandidateProfile profile;
  const PreferencesScreen({super.key, required this.profile});
  @override
  State<PreferencesScreen> createState() => _PreferencesScreenState();
}

class _PreferencesScreenState extends State<PreferencesScreen> {
  late final _sector = TextEditingController(text: widget.profile.sector ?? '');
  late final _offer = TextEditingController(
    text: widget.profile.targetOfferDescription ?? '',
  );
  late final _availability = TextEditingController(
    text: widget.profile.availability ?? '',
  );
  late final _language = TextEditingController(
    text: widget.profile.interviewLanguage ?? '',
  );
  late final _interests = TextEditingController(
    text: widget.profile.interests.join(', '),
  );
  late final _objective = TextEditingController(
    text: widget.profile.trainingObjective ?? '',
  );
  late bool _mobility = widget.profile.mobility;
  bool _loading = false;

  Future<void> _save() async {
    setState(() => _loading = true);
    final p = widget.profile
      ..sector = _sector.text.trim()
      ..targetOfferDescription = _offer.text.trim()
      ..availability = _availability.text.trim()
      ..interviewLanguage = _language.text.trim()
      ..interests = _interests.text
          .split(',')
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toList()
      ..trainingObjective = _objective.text.trim()
      ..mobility = _mobility;
    try {
      await CandidateService().savePreferences(p);
      if (mounted) Navigator.pop(context, true);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Erreur lors de l'enregistrement")),
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Préférences professionnelles')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _sector,
            decoration: const InputDecoration(labelText: 'Secteur'),
          ),
          TextField(
            controller: _offer,
            maxLines: 3,
            decoration: const InputDecoration(
              labelText: "Description de l'offre souhaitée",
            ),
          ),
          TextField(
            controller: _availability,
            decoration: const InputDecoration(labelText: 'Disponibilité'),
          ),
          SwitchListTile(
            title: const Text('Prêt à me déplacer'),
            value: _mobility,
            onChanged: (v) => setState(() => _mobility = v),
          ),
          TextField(
            controller: _language,
            decoration: const InputDecoration(
              labelText: "Langue de l'entretien",
            ),
          ),
          TextField(
            controller: _interests,
            decoration: const InputDecoration(
              labelText: "Centres d'intérêt (séparés par des virgules)",
            ),
          ),
          TextField(
            controller: _objective,
            decoration: const InputDecoration(
              labelText: "Objectif d'entraînement",
            ),
          ),
          const SizedBox(height: 24),
          ElevatedButton(
            onPressed: _loading ? null : _save,
            child: _loading
                ? const CircularProgressIndicator()
                : const Text('Enregistrer'),
          ),
        ],
      ),
    );
  }
}
