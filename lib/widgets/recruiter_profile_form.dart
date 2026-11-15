import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import '../models/recruiter_profile.dart';
import '../services/recruiter_service.dart';
import 'app_dropdown.dart';
import 'app_text_field.dart';

class RecruiterProfileForm extends StatefulWidget {
  final RecruiterProfile? initial;
  final String submitLabel;
  final Future<void> Function(Map<String, dynamic> data) onSubmit;

  const RecruiterProfileForm({
    super.key,
    this.initial,
    required this.submitLabel,
    required this.onSubmit,
  });

  @override
  State<RecruiterProfileForm> createState() => _RecruiterProfileFormState();
}

class _RecruiterProfileFormState extends State<RecruiterProfileForm> {
  static const _sizes = {
    '1-10': '1 à 10 employés',
    '11-50': '11 à 50 employés',
    '51-200': '51 à 200 employés',
    '201-1000': '201 à 1000 employés',
    '1000+': 'Plus de 1000 employés',
  };

  final _formKey = GlobalKey<FormState>();
  final _fullName = TextEditingController();
  final _position = TextEditingController();
  final _company = TextEditingController();
  final _website = TextEditingController();
  final _country = TextEditingController();
  final _city = TextEditingController();
  final _description = TextEditingController();
  final _hiringNeeds = TextEditingController();
  String? _sectorId;
  String? _size;
  final Set<String> _domainIds = {};

  List<RefItem> _sectors = [];
  List<RefItem> _domains = [];
  bool _loading = true;
  bool _saving = false;
  String? _loadError;

  @override
  void initState() {
    super.initState();
    final p = widget.initial;
    _fullName.text = p?.fullName ?? FirebaseAuth.instance.currentUser?.displayName ?? '';
    if (p != null) {
      _position.text = p.position;
      _company.text = p.companyName;
      _website.text = p.companyWebsite ?? '';
      _country.text = p.country;
      _city.text = p.city;
      _description.text = p.companyDescription;
      _hiringNeeds.text = p.hiringNeeds ?? '';
      _sectorId = p.sector?.id;
      _size = _sizes.containsKey(p.companySize) ? p.companySize : null;
      _domainIds.addAll(p.domains.map((d) => d.id));
    }
    _loadReferences();
  }

  Future<void> _loadReferences() async {
    if (!_loading) {
      setState(() {
        _loading = true;
        _loadError = null;
      });
    }
    try {
      final s = RecruiterService();
      final r = await Future.wait([s.sectors(), s.domains()]);
      if (!mounted) return;
      setState(() {
        _sectors = r[0];
        _domains = r[1];
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loadError = '$e';
        _loading = false;
      });
    }
  }

  String _normalizeWebsite(String raw) {
    final w = raw.trim();
    if (w.isEmpty) return '';
    return RegExp(r'^https?://', caseSensitive: false).hasMatch(w)
        ? w
        : 'https://$w';
  }

  String? _validateWebsite(String? v) {
    final w = (v ?? '').trim();
    if (w.isEmpty) return null;
    final ok = RegExp(r'^(https?://)?([\w-]+\.)+[\w-]{2,}(/\S*)?$',
        caseSensitive: false)
        .hasMatch(w);
    return ok ? null : 'Site web invalide (ex : monentreprise.com)';
  }

  @override
  void dispose() {
    for (final c in [_fullName, _position, _company, _website, _country,
      _city, _description, _hiringNeeds]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_domainIds.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Choisissez au moins un domaine de recrutement')));
      return;
    }
    setState(() => _saving = true);
    try {
      await widget.onSubmit({
        'fullName': _fullName.text.trim(),
        'position': _position.text.trim(),
        'companyName': _company.text.trim(),
        'sectorId': _sectorId,
        'companySize': _size,
        'companyWebsite': _normalizeWebsite(_website.text),
        'country': _country.text.trim(),
        'city': _city.text.trim(),
        'companyDescription': _description.text.trim(),
        'hiringNeeds': _hiringNeeds.text.trim(),
        'domainIds': _domainIds.toList(),
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_loadError != null) {
      return Center(child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('Chargement impossible :\n$_loadError',
              textAlign: TextAlign.center),
          const SizedBox(height: 12),
          ElevatedButton(
              onPressed: _loadReferences, child: const Text('Réessayer')),
        ]),
      ));
    }
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Form(
        key: _formKey,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Vous', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          AppTextField(controller: _fullName, label: 'Nom complet'),
          AppTextField(controller: _position, label: 'Poste occupé'),
          const SizedBox(height: 8),
          Text('Votre entreprise', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          AppTextField(controller: _company, label: "Nom de l'entreprise"),
          AppDropdown(
            label: "Secteur d'activité",
            value: _sectorId,
            items: {for (final s in _sectors) s.id: s.name},
            onChanged: (v) => setState(() => _sectorId = v),
          ),
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
            validator: _validateWebsite,
          ),
          AppTextField(controller: _country, label: 'Pays'),
          AppTextField(controller: _city, label: 'Ville'),
          AppTextField(
              controller: _description,
              label: "Description de l'entreprise",
              maxLines: 4),
          const SizedBox(height: 8),
          Text('Domaines de recrutement *',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: _domains.map((d) {
              final selected = _domainIds.contains(d.id);
              return FilterChip(
                label: Text(d.name),
                selected: selected,
                onSelected: (v) => setState(
                        () => v ? _domainIds.add(d.id) : _domainIds.remove(d.id)),
              );
            }).toList(),
          ),
          const SizedBox(height: 14),
          AppTextField(
              controller: _hiringNeeds,
              label: 'Profils / compétences recherchés',
              required: false,
              maxLines: 3),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _saving ? null : _submit,
              child: _saving
                  ? const SizedBox(
                  height: 20, width: 20,
                  child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(widget.submitLabel),
            ),
          ),
        ]),
      ),
    );
  }
}