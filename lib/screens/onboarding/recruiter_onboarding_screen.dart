import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import '../../services/auth_service.dart';
import '../../services/profile_service.dart';
import '../../widgets/app_dropdown.dart';
import '../../widgets/app_text_field.dart';
import '../auth_gate.dart';

class RecruiterOnboardingScreen extends StatefulWidget {
  const RecruiterOnboardingScreen({super.key});

  @override
  State<RecruiterOnboardingScreen> createState() =>
      _RecruiterOnboardingScreenState();
}

class _RecruiterOnboardingScreenState extends State<RecruiterOnboardingScreen> {
  final _formKey = GlobalKey<FormState>();
  bool _saving = false;

  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _position = TextEditingController();
  final _company = TextEditingController();
  final _sector = TextEditingController();
  final _website = TextEditingController();
  final _country = TextEditingController();
  final _city = TextEditingController();
  final _description = TextEditingController();
  final _hiringNeeds = TextEditingController();
  String? _size;

  static const _sizes = {
    '1-10': '1 à 10 employés',
    '11-50': '11 à 50 employés',
    '51-200': '51 à 200 employés',
    '201-1000': '201 à 1000 employés',
    '1000+': 'Plus de 1000 employés',
  };

  @override
  void initState() {
    super.initState();
    final name = FirebaseAuth.instance.currentUser?.displayName ?? '';
    final parts = name.trim().split(' ');
    if (parts.isNotEmpty) _firstName.text = parts.first;
    if (parts.length > 1) _lastName.text = parts.sublist(1).join(' ');
  }

  @override
  void dispose() {
    for (final c in [
      _firstName, _lastName, _position, _company, _sector, _website,
      _country, _city, _description, _hiringNeeds,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      await ProfileService().saveRecruiter(
        {
          'company_name': _company.text.trim(),
          'position': _position.text.trim(),
          'company_sector': _sector.text.trim(),
          'company_size': _size,
          'company_website': _website.text.trim(),
          'country': _country.text.trim(),
          'city': _city.text.trim(),
          'company_description': _description.text.trim(),
          'hiring_needs': _hiringNeeds.text.trim(),
        },
        fullName: '${_firstName.text.trim()} ${_lastName.text.trim()}',
      );
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const AuthGate()),
            (_) => false,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Erreur : $e')));
    }
  }

  Future<void> _logout() async {
    await AuthService().signOut();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const AuthGate()),
          (_) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profil recruteur'),
        actions: [
          IconButton(
              icon: const Icon(Icons.logout),
              tooltip: 'Se déconnecter',
              onPressed: _logout),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: Column(children: [
            AppTextField(controller: _firstName, label: 'Prénom'),
            AppTextField(controller: _lastName, label: 'Nom'),
            AppTextField(controller: _position, label: 'Poste occupé'),
            AppTextField(controller: _company, label: 'Nom de la société'),
            AppTextField(
                controller: _sector, label: "Secteur / domaine d'activité"),
            AppDropdown(
              label: "Taille de l'entreprise",
              value: _size,
              items: _sizes,
              onChanged: (v) => setState(() => _size = v),
            ),
            AppTextField(
              controller: _website,
              label: 'Site web',
              required: false,
              keyboardType: TextInputType.url,
            ),
            AppTextField(controller: _country, label: 'Pays'),
            AppTextField(controller: _city, label: 'Ville'),
            AppTextField(
              controller: _description,
              label: 'Description de la société',
              maxLines: 4,
            ),
            AppTextField(
              controller: _hiringNeeds,
              label: 'Profils / compétences recherchés',
              maxLines: 3,
              required: false,
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saving ? null : _submit,
                child: _saving
                    ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Terminer'),
              ),
            ),
          ]),
        ),
      ),
    );
  }
}