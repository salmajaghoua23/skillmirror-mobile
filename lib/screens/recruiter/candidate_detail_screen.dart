import 'package:flutter/material.dart';

import '../../models/job_offer.dart';
import '../../services/recruiter_offer_service.dart';

/// Profil d'un candidat autorisé (consentement ou candidature).
class CandidateDetailScreen extends StatelessWidget {
  final String profileId;
  const CandidateDetailScreen({super.key, required this.profileId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Profil candidat')),
      body: FutureBuilder<CandidateDetail>(
        future: RecruiterOfferService().candidate(profileId),
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text('${snap.error}', textAlign: TextAlign.center),
              ),
            );
          }
          return _body(context, snap.data!);
        },
      ),
    );
  }

  Widget _body(BuildContext context, CandidateDetail c) {
    final t = Theme.of(context).textTheme;

    Widget section(String title, List<Widget> children) {
      if (children.isEmpty) return const SizedBox.shrink();
      return Card(
        margin: const EdgeInsets.only(bottom: 16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: t.titleMedium),
              const Divider(),
              ...children,
            ],
          ),
        ),
      );
    }

    Widget line(String label, String? value) => (value == null || value.isEmpty)
        ? const SizedBox.shrink()
        : Padding(
            padding: const EdgeInsets.symmetric(vertical: 3),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [Text(label, style: t.bodySmall), Text(value)],
            ),
          );

    Widget chips(List<String> items) => Wrap(
      spacing: 8,
      runSpacing: 4,
      children: [for (final s in items) Chip(label: Text(s))],
    );

    String s(dynamic v) => (v == null) ? '' : v.toString();

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(c.fullName, style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        if (c.email != null) Text(c.email!),
        const SizedBox(height: 16),
        if (c.summary != null && c.summary!.isNotEmpty)
          section('Résumé', [Text(c.summary!)]),
        section('Informations', [
          line('Domaine d\'études', c.studyField),
          line('Niveau', c.educationLevel),
          line('Diplôme', c.diploma),
          line('Secteur visé', c.sector),
          line('Disponibilité', c.availability),
          line('Lieu', [c.region, c.country].whereType<String>().join(', ')),
          if (c.mobility != null) line('Mobilité', c.mobility! ? 'Oui' : 'Non'),
        ]),
        section('Compétences techniques',
            c.technicalSkills.isEmpty ? [] : [chips(c.technicalSkills)]),
        section('Compétences comportementales',
            c.softSkills.isEmpty ? [] : [chips(c.softSkills)]),
        section('Expériences', [
          for (final e in c.experiences)
            line('${s(e['company'])} · ${s(e['startDate'])} - ${s(e['endDate'])}',
                '${s(e['title'])}\n${s(e['description'])}'.trim()),
        ]),
        section('Formations', [
          for (final e in c.education)
            line('${s(e['institution'])} · ${s(e['startYear'])} - ${s(e['endYear'])}',
                '${s(e['degree'])} ${s(e['field'])}'.trim()),
        ]),
        section('Langues', [
          for (final l in c.languages) line(s(l['name']), s(l['level'])),
        ]),
        section('Certifications', [
          for (final x in c.certifications)
            line(s(x['name']), '${s(x['issuer'])} ${s(x['year'])}'.trim()),
        ]),
      ],
    );
  }
}
