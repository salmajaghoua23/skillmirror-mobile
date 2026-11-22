import 'package:flutter/material.dart';

import '../../models/job_offer.dart';
import '../../services/recruiter_offer_service.dart';
import '../../widgets/app_dropdown.dart';
import '../../widgets/app_text_field.dart';
import '../../widgets/primary_button.dart';
import '../../models/skill_reference.dart';
import '../../services/skill_service.dart';
import '../../widgets/skill_picker.dart';
/// Création / modification d'une offre + gestion des compétences demandées.
class OfferFormScreen extends StatefulWidget {
  final JobOffer? initial; // null = création
  const OfferFormScreen({super.key, this.initial});

  @override
  State<OfferFormScreen> createState() => _OfferFormScreenState();
}

class _OfferFormScreenState extends State<OfferFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _description = TextEditingController();
  final _location = TextEditingController();
  final _salary = TextEditingController();
  final _skillInput = TextEditingController();

  String? _contract;
  String? _experience;
  String _status = 'DRAFT';
  bool _remote = false;
  final List<OfferSkill> _skills = [];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final o = widget.initial;
    if (o != null) {
      _title.text = o.title;
      _description.text = o.description;
      _location.text = o.location ?? '';
      _salary.text = o.salaryRange ?? '';
      _contract = o.contractType;
      _experience = o.experienceLevel;
      _status = o.status == 'CLOSED' ? 'DRAFT' : o.status;
      _remote = o.remote;
      _skills.addAll(o.skills);
    }
  }

  @override
  void dispose() {
    for (final c in [_title, _description, _location, _salary, _skillInput]) {
      c.dispose();
    }
    super.dispose();
  }

  void _addSkill() {
    final name = _skillInput.text.trim();
    if (name.isEmpty) return;
    final exists = _skills.any((s) => s.name.toLowerCase() == name.toLowerCase());
    if (!exists) setState(() => _skills.add(OfferSkill(name)));
    _skillInput.clear();
  }

  void _toggleRequired(int i) => setState(
    () => _skills[i] = OfferSkill(_skills[i].name, required: !_skills[i].required),
  );
  Future<void> _pickFromReferential() async {
    final res = await showSkillPicker(
      context,
      selected: _skills.map((s) => s.name).toSet(),
    );
    if (res == null) return;
    setState(() {
      _skills.removeWhere(
            (s) => res.known.contains(s.name) && !res.chosen.contains(s.name),
      );
      for (final name in res.chosen) {
        if (!_skills.any((s) => s.name.toLowerCase() == name.toLowerCase())) {
          _skills.add(OfferSkill(name));
        }
      }
    });
  }

  Future<void> _importFromRole() async {
    try {
      final svc = SkillService();
      final roles = await svc.roles();
      if (!mounted) return;
      final role = await showDialog<JobRoleItem>(
        context: context,
        builder: (ctx) => SimpleDialog(
          title: const Text('Choisir un métier'),
          children: [
            for (final r in roles)
              SimpleDialogOption(
                onPressed: () => Navigator.pop(ctx, r),
                child: Text(r.name),
              ),
          ],
        ),
      );
      if (role == null) return;
      final detail = await svc.role(role.id);
      if (!mounted) return;
      var added = 0;
      setState(() {
        for (final s in detail.skills) {
          if (!_skills.any((x) => x.name.toLowerCase() == s.name.toLowerCase())) {
            _skills.add(OfferSkill(s.name, required: s.required));
            added++;
          }
        }
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$added compétence(s) ajoutée(s) pour « ${role.name} »')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }
  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final data = {
      'title': _title.text.trim(),
      'description': _description.text.trim(),
      'location': _location.text.trim(),
      'contractType': _contract,
      'experienceLevel': _experience ?? '',
      'remote': _remote,
      'salaryRange': _salary.text.trim(),
      'status': _status,
      'skills': _skills.map((s) => s.toJson()).toList(),
    };
    try {
      final svc = RecruiterOfferService();
      if (widget.initial == null) {
        await svc.create(data);
      } else {
        await svc.update(widget.initial!.id, data);
      }
      if (!mounted) return;
      Navigator.pop(context, true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    final editing = widget.initial != null;
    return Scaffold(
      appBar: AppBar(title: Text(editing ? "Modifier l'offre" : 'Nouvelle offre')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            AppTextField(controller: _title, label: "Titre du poste"),
            AppTextField(
                controller: _description, label: 'Description', maxLines: 6),
            AppTextField(controller: _location, label: 'Lieu', required: false),
            AppDropdown(
              label: 'Type de contrat',
              value: _contract,
              items: OfferLabels.contractTypes,
              onChanged: (v) => setState(() => _contract = v),
            ),
            AppDropdown(
              label: "Niveau d'expérience",
              value: _experience,
              items: OfferLabels.experienceLevels,
              required: false,
              onChanged: (v) => setState(() => _experience = v),
            ),
            AppTextField(
                controller: _salary, label: 'Salaire (ex : 8 000 - 12 000 MAD)', required: false),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Télétravail possible'),
              value: _remote,
              onChanged: (v) => setState(() => _remote = v),
            ),
            const SizedBox(height: 8),
            Text('Compétences demandées',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text('Touchez une compétence pour la rendre obligatoire (★) ou optionnelle.',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: _pickFromReferential,
                  icon: const Icon(Icons.list_alt),
                  label: const Text('Référentiel'),
                ),
                OutlinedButton.icon(
                  onPressed: _importFromRole,
                  icon: const Icon(Icons.work_outline),
                  label: const Text('Depuis un métier'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _skillInput,
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _addSkill(),
                    decoration: const InputDecoration(
                      labelText: 'Ex : Java, Spring Boot, Communication',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.filled(
                  onPressed: _addSkill,
                  icon: const Icon(Icons.add),
                  tooltip: 'Ajouter',
                ),
              ],
            ),
            const SizedBox(height: 8),

            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [
                for (var i = 0; i < _skills.length; i++)
                  InputChip(
                    label: Text('${_skills[i].required ? '★ ' : ''}${_skills[i].name}'),
                    onPressed: () => _toggleRequired(i),
                    onDeleted: () => setState(() => _skills.removeAt(i)),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            AppDropdown(
              label: 'Statut',
              value: _status,
              items: const {'DRAFT': 'Brouillon', 'PUBLISHED': 'Publiée'},
              onChanged: (v) => setState(() => _status = v ?? 'DRAFT'),
            ),
            const SizedBox(height: 8),
            PrimaryButton(
              label: _saving ? 'Enregistrement…' : 'Enregistrer',
              onPressed: _saving ? null : _submit,
            ),
          ],
        ),
      ),
    );
  }
}
