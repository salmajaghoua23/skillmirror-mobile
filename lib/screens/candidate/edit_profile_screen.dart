import 'package:flutter/material.dart';

import '../../models/candidate_profile.dart';
import '../../services/candidate_service.dart';

class EditProfileScreen extends StatefulWidget {
  final CandidateProfile profile;
  const EditProfileScreen({super.key, required this.profile});
  @override
  State<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends State<EditProfileScreen> {
  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.profile.fullName);
  late final _age = TextEditingController(
    text: widget.profile.age?.toString() ?? '',
  );
  late final _country = TextEditingController(
    text: widget.profile.country ?? '',
  );
  late final _region = TextEditingController(text: widget.profile.region ?? '');
  late final _study = TextEditingController(
    text: widget.profile.studyField ?? '',
  );
  late final _level = TextEditingController(
    text: widget.profile.educationLevel ?? '',
  );
  late final _diploma = TextEditingController(
    text: widget.profile.diploma ?? '',
  );
  late String? _gender = widget.profile.gender;
  bool _loading = false;

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _loading = true);
    final p = widget.profile
      ..fullName = _name.text.trim()
      ..age = int.tryParse(_age.text)
      ..gender = _gender
      ..country = _country.text.trim()
      ..region = _region.text.trim()
      ..studyField = _study.text.trim()
      ..educationLevel = _level.text.trim()
      ..diploma = _diploma.text.trim();
    try {
      await CandidateService().updateCandidateProfile(p);
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
      appBar: AppBar(title: const Text('Modifier mon profil')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(labelText: 'Nom complet'),
              validator: (v) =>
                  v == null || v.trim().isEmpty ? 'Obligatoire' : null,
            ),
            TextFormField(
              controller: _age,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Âge'),
              validator: (v) {
                final n = int.tryParse(v ?? '');
                return n == null || n < 16 || n > 80
                    ? 'Âge entre 16 et 80'
                    : null;
              },
            ),
            DropdownButtonFormField<String>(
              value: _gender,
              decoration: const InputDecoration(labelText: 'Sexe'),
              items: const [
                DropdownMenuItem(value: 'M', child: Text('Masculin')),
                DropdownMenuItem(value: 'F', child: Text('Féminin')),
                DropdownMenuItem(
                  value: 'NOT_SPECIFIED',
                  child: Text('Non précisé'),
                ),
              ],
              onChanged: (v) => setState(() => _gender = v),
              validator: (v) => v == null ? 'Obligatoire' : null,
            ),
            TextFormField(
              controller: _country,
              decoration: const InputDecoration(labelText: 'Pays'),
            ),
            TextFormField(
              controller: _region,
              decoration: const InputDecoration(labelText: 'Région'),
            ),
            TextFormField(
              controller: _study,
              decoration: const InputDecoration(labelText: "Domaine d'études"),
            ),
            TextFormField(
              controller: _level,
              decoration: const InputDecoration(labelText: "Niveau d'études"),
            ),
            TextFormField(
              controller: _diploma,
              decoration: const InputDecoration(labelText: 'Diplôme'),
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
      ),
    );
  }
}
