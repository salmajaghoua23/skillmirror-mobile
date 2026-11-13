import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';

import '../../services/auth_service.dart';
import '../../services/cv_service.dart';
import '../../services/profile_service.dart';
import '../../widgets/app_dropdown.dart';
import '../../widgets/app_text_field.dart';
import '../auth_gate.dart';

class CandidateOnboardingScreen extends StatefulWidget {
  const CandidateOnboardingScreen({super.key});

  @override
  State<CandidateOnboardingScreen> createState() =>
      _CandidateOnboardingScreenState();
}

class _CandidateOnboardingScreenState extends State<CandidateOnboardingScreen> {
  final _pageController = PageController();
  final _formKeys = List.generate(3, (_) => GlobalKey<FormState>());
  int _step = 0;
  bool _saving = false;
  bool _uploading = false;

  // Étape 1 : identité
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _age = TextEditingController();
  final _country = TextEditingController();
  final _region = TextEditingController();
  String? _gender;

  // Étape 2 : parcours
  final _sector = TextEditingController();
  final _studyField = TextEditingController();
  final _diploma = TextEditingController();
  final _interests = TextEditingController();
  String? _educationLevel;
  PickedCv? _cv;

  // Étape 3 : recherche
  final _targetOffer = TextEditingController();
  final _trainingObjective = TextEditingController();
  String? _availability;
  String? _interviewLanguage;
  bool _mobility = false;

  static const _genders = {
    'M': 'Homme',
    'F': 'Femme',
    'NOT_SPECIFIED': 'Je préfère ne pas préciser',
  };
  static const _levels = {
    'bac': 'Bac',
    'bac+2': 'Bac +2',
    'bac+3': 'Bac +3 (Licence)',
    'bac+5': 'Bac +5 (Master / Ingénieur)',
    'doctorat': 'Doctorat',
    'autre': 'Autre',
  };
  static const _availabilities = {
    'immediate': 'Immédiate',
    '1_month': 'Dans 1 mois',
    '3_months': 'Dans 3 mois',
    'internship': 'Stage uniquement',
  };
  static const _languages = {'fr': 'Français', 'en': 'Anglais', 'ar': 'Arabe'};

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
      _firstName,
      _lastName,
      _age,
      _country,
      _region,
      _sector,
      _studyField,
      _diploma,
      _interests,
      _targetOffer,
      _trainingObjective,
    ]) {
      c.dispose();
    }
    _pageController.dispose();
    super.dispose();
  }

  void _goTo(int step) {
    setState(() => _step = step);
    _pageController.animateToPage(
      step,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
    );
  }

  void _next() {
    if (!_formKeys[_step].currentState!.validate()) return;
    if (_step < 2) {
      _goTo(_step + 1);
    } else {
      _submit();
    }
  }

  Future<void> _pickCv() async {
    setState(() => _uploading = true);
    try {
      final cv = await CvService().pickAndUpload();
      if (cv != null) setState(() => _cv = cv);
    } catch (e) {
      _showError('Import du CV impossible : $e');
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _submit() async {
    setState(() => _saving = true);
    try {
      final service = ProfileService();
      final interests = _interests.text
          .split(',')
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toList();

      await service.saveCandidate({
        'age': int.parse(_age.text.trim()),
        'gender': _gender,
        'sector': _sector.text.trim(),
        'study_field': _studyField.text.trim(),
        'education_level': _educationLevel,
        'diploma': _diploma.text.trim(),
        'country': _country.text.trim(),
        'region': _region.text.trim(),
        'availability': _availability,
        'mobility': _mobility,
        'interview_language': _interviewLanguage,
        'interests': interests,
        'target_offer_description': _targetOffer.text.trim(),
        'training_objective': _trainingObjective.text.trim(),
        'cv_storage_path': _cv?.storagePath,
      }, fullName: '${_firstName.text.trim()} ${_lastName.text.trim()}');
      if (_cv != null) {
        await CvService().register(await service.profileId(), _cv!);
      }
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const AuthGate()),
        (_) => false,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      _showError('Erreur : $e');
    }
  }

  void _showError(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
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
        title: Text('Profil candidat (${_step + 1}/3)'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Se déconnecter',
            onPressed: _logout,
          ),
        ],
      ),
      body: Column(
        children: [
          LinearProgressIndicator(value: (_step + 1) / 3),
          Expanded(
            child: PageView(
              controller: _pageController,
              physics: const NeverScrollableScrollPhysics(),
              children: [_stepIdentity(), _stepBackground(), _stepSearch()],
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  if (_step > 0)
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _saving ? null : () => _goTo(_step - 1),
                        child: const Text('Retour'),
                      ),
                    ),
                  if (_step > 0) const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: _saving ? null : _next,
                      child: _saving
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(_step < 2 ? 'Suivant' : 'Terminer'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _stepIdentity() => SingleChildScrollView(
    padding: const EdgeInsets.all(16),
    child: Form(
      key: _formKeys[0],
      child: Column(
        children: [
          AppTextField(controller: _firstName, label: 'Prénom'),
          AppTextField(controller: _lastName, label: 'Nom'),
          AppTextField(
            controller: _age,
            label: 'Âge',
            keyboardType: TextInputType.number,
            validator: (v) {
              final n = int.tryParse((v ?? '').trim());
              if (n == null) return 'Entrez un nombre';
              if (n < 16 || n > 80) return 'Âge entre 16 et 80';
              return null;
            },
          ),
          AppDropdown(
            label: 'Sexe',
            value: _gender,
            items: _genders,
            onChanged: (v) => setState(() => _gender = v),
          ),
          AppTextField(controller: _country, label: 'Pays'),
          AppTextField(controller: _region, label: 'Région / Ville'),
        ],
      ),
    ),
  );

  Widget _stepBackground() => SingleChildScrollView(
    padding: const EdgeInsets.all(16),
    child: Form(
      key: _formKeys[1],
      child: Column(
        children: [
          AppTextField(
            controller: _sector,
            label: 'Secteur (ex : Informatique)',
          ),
          AppTextField(controller: _studyField, label: "Domaine d'études"),
          AppDropdown(
            label: "Niveau d'études",
            value: _educationLevel,
            items: _levels,
            onChanged: (v) => setState(() => _educationLevel = v),
          ),
          AppTextField(controller: _diploma, label: 'Diplôme'),
          AppTextField(
            controller: _interests,
            label: "Centres d'intérêt (séparés par des virgules)",
            required: false,
          ),
          const SizedBox(height: 4),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              onPressed: _uploading ? null : _pickCv,
              icon: _uploading
                  ? const SizedBox(
                      height: 16,
                      width: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.upload_file),
              label: Text(
                _cv == null
                    ? 'Importer mon CV (PDF / DOCX)'
                    : 'CV : ${_cv!.fileName}',
              ),
            ),
          ),
        ],
      ),
    ),
  );

  Widget _stepSearch() => SingleChildScrollView(
    padding: const EdgeInsets.all(16),
    child: Form(
      key: _formKeys[2],
      child: Column(
        children: [
          AppDropdown(
            label: 'Disponibilité',
            value: _availability,
            items: _availabilities,
            onChanged: (v) => setState(() => _availability = v),
          ),
          AppDropdown(
            label: "Langue de l'entretien",
            value: _interviewLanguage,
            items: _languages,
            onChanged: (v) => setState(() => _interviewLanguage = v),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Prêt(e) à me déplacer'),
            value: _mobility,
            onChanged: (v) => setState(() => _mobility = v),
          ),
          const SizedBox(height: 8),
          AppTextField(
            controller: _targetOffer,
            label: "Description de l'offre souhaitée",
            maxLines: 4,
          ),
          AppTextField(
            controller: _trainingObjective,
            label: "Objectif de l'entraînement",
            maxLines: 3,
          ),
        ],
      ),
    ),
  );
}
