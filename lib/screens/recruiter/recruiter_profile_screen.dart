import 'package:flutter/material.dart';

import '../../models/recruiter_profile.dart';
import '../../services/recruiter_service.dart';
import 'recruiter_edit_screen.dart';

class RecruiterProfileScreen extends StatefulWidget {
  const RecruiterProfileScreen({super.key});

  @override
  State<RecruiterProfileScreen> createState() => _RecruiterProfileScreenState();
}

class _RecruiterProfileScreenState extends State<RecruiterProfileScreen> {
  late Future<RecruiterProfile> _future = RecruiterService().getProfile();

  void _reload() => setState(() => _future = RecruiterService().getProfile());

  Future<void> _edit(RecruiterProfile p) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => RecruiterEditScreen(profile: p)),
    );
    if (changed == true) _reload();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<RecruiterProfile>(
      future: _future,
      builder: (context, snap) {
        final p = snap.data;
        return Scaffold(
          appBar: AppBar(
            title: const Text('Mon profil'),
            actions: [
              if (p != null)
                IconButton(
                  icon: const Icon(Icons.edit),
                  tooltip: 'Modifier',
                  onPressed: () => _edit(p),
                ),
            ],
          ),
          body: () {
            if (snap.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text('Erreur : ${snap.error}'),
                    TextButton(
                      onPressed: _reload,
                      child: const Text('Réessayer'),
                    ),
                  ],
                ),
              );
            }
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _section('Contact', [
                  _row('Nom', p!.fullName),
                  _row('Email', p.email),
                  _row('Poste', p.position),
                ]),
                _section('Entreprise', [
                  _row('Nom', p.companyName),
                  _row("Secteur", p.sector?.name ?? '—'),
                  _row('Taille', p.companySize),
                  _row('Site web', p.companyWebsite ?? '—'),
                  _row('Lieu', '${p.city}, ${p.country}'),
                  _row('Description', p.companyDescription),
                ]),
                _section('Recrutement', [
                  const Text('Domaines'),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 8,
                    children: [
                      for (final d in p.domains) Chip(label: Text(d.name)),
                    ],
                  ),
                  const SizedBox(height: 8),
                  _row('Profils recherchés', p.hiringNeeds ?? '—'),
                ]),
              ],
            );
          }(),
        );
      },
    );
  }

  Widget _section(String title, List<Widget> children) => Card(
    margin: const EdgeInsets.only(bottom: 16),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const Divider(),
          ...children,
        ],
      ),
    ),
  );

  Widget _row(String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        Text(value),
      ],
    ),
  );
}
