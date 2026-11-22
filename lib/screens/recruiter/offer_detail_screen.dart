import 'package:flutter/material.dart';

import '../../models/job_offer.dart';
import '../../services/recruiter_offer_service.dart';
import '../../widgets/status_chip.dart';
import 'candidate_detail_screen.dart';
import 'offer_form_screen.dart';

/// Détail d'une offre : informations, candidatures reçues, candidats correspondants.
class OfferDetailScreen extends StatefulWidget {
  final String offerId;
  const OfferDetailScreen({super.key, required this.offerId});

  @override
  State<OfferDetailScreen> createState() => _OfferDetailScreenState();
}

class _OfferDetailScreenState extends State<OfferDetailScreen> {
  final _svc = RecruiterOfferService();
  late Future<JobOffer> _offer = _svc.offer(widget.offerId);
  late Future<List<JobApplication>> _apps = _svc.applications(widget.offerId);
  late Future<List<CandidateSummary>> _matches = _svc.matches(widget.offerId);

  void _reload() => setState(() {
    _offer = _svc.offer(widget.offerId);
    _apps = _svc.applications(widget.offerId);
    _matches = _svc.matches(widget.offerId);
  });

  void _toast(String m) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));

  Future<void> _run(Future<void> Function() action) async {
    try {
      await action();
      if (mounted) _reload();
    } catch (e) {
      if (mounted) _toast(e.toString());
    }
  }

  Future<void> _edit(JobOffer o) async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => OfferFormScreen(initial: o)),
    );
    if (changed == true && mounted) _reload();
  }

  Future<void> _delete(JobOffer o) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text("Supprimer l'offre ?"),
        content: const Text('Ses candidatures seront aussi supprimées.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Annuler')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Supprimer')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await _svc.delete(o.id);
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) _toast(e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<JobOffer>(
      future: _offer,
      builder: (context, snap) {
        final o = snap.data;
        return DefaultTabController(
          length: 3,
          child: Scaffold(
            appBar: AppBar(
              title: Text(o?.title ?? 'Offre'),
              actions: [
                if (o != null)
                  IconButton(
                    icon: const Icon(Icons.edit),
                    tooltip: 'Modifier',
                    onPressed: () => _edit(o),
                  ),
                if (o != null)
                  PopupMenuButton<String>(
                    onSelected: (v) {
                      if (v == 'DELETE') {
                        _delete(o);
                      } else {
                        _run(() => _svc.setStatus(o.id, v));
                      }
                    },
                    itemBuilder: (_) => [
                      if (o.status != 'PUBLISHED')
                        const PopupMenuItem(value: 'PUBLISHED', child: Text('Publier')),
                      if (o.status == 'PUBLISHED')
                        const PopupMenuItem(value: 'CLOSED', child: Text('Clôturer')),
                      if (o.status != 'DRAFT')
                        const PopupMenuItem(value: 'DRAFT', child: Text('Repasser en brouillon')),
                      const PopupMenuItem(value: 'DELETE', child: Text('Supprimer')),
                    ],
                  ),
              ],
              bottom: const TabBar(tabs: [
                Tab(text: 'Détails'),
                Tab(text: 'Candidatures'),
                Tab(text: 'Correspondants'),
              ]),
            ),
            body: () {
              if (snap.connectionState != ConnectionState.done) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snap.hasError) {
                return Center(child: Text('Erreur : ${snap.error}'));
              }
              return TabBarView(children: [
                _details(o!),
                _applicationsTab(),
                _matchesTab(),
              ]);
            }(),
          ),
        );
      },
    );
  }

  // ---------------- Onglet Détails ----------------
  Widget _details(JobOffer o) {
    final t = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(children: [
          StatusChip(
            status: o.status,
            label: OfferLabels.offerStatuses[o.status] ?? o.status,
          ),
          const Spacer(),
          Text('Créée le ${shortDate(o.createdAt)}', style: t.bodySmall),
        ]),
        const SizedBox(height: 12),
        Text(o.title, style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text([
          OfferLabels.contractTypes[o.contractType] ?? o.contractType,
          if (o.experienceLevel != null)
            OfferLabels.experienceLevels[o.experienceLevel] ?? o.experienceLevel!,
          if (o.location != null) o.location!,
          if (o.remote) 'Télétravail',
          if (o.salaryRange != null) o.salaryRange!,
        ].join(' · ')),
        const SizedBox(height: 16),
        Text('Description', style: t.titleMedium),
        const SizedBox(height: 4),
        Text(o.description),
        const SizedBox(height: 16),
        Text('Compétences demandées', style: t.titleMedium),
        const SizedBox(height: 8),
        if (o.skills.isEmpty)
          const Text('Aucune compétence renseignée (les candidats correspondants '
              'ne peuvent pas être calculés).'),
        Wrap(spacing: 8, runSpacing: 4, children: [
          for (final s in o.skills)
            Chip(label: Text('${s.required ? '★ ' : ''}${s.name}')),
        ]),
      ],
    );
  }

  // ---------------- Onglet Candidatures ----------------
  Widget _applicationsTab() => FutureBuilder<List<JobApplication>>(
    future: _apps,
    builder: (context, snap) {
      if (snap.connectionState != ConnectionState.done) {
        return const Center(child: CircularProgressIndicator());
      }
      if (snap.hasError) return Center(child: Text('Erreur : ${snap.error}'));
      final list = snap.data!;
      if (list.isEmpty) return const Center(child: Text('Aucune candidature reçue.'));
      return ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: list.length,
        itemBuilder: (_, i) {
          final a = list[i];
          return Card(
            child: ListTile(
              title: Text(a.candidateName),
              subtitle: Text([
                if (a.matchScore != null) 'Correspondance ${a.matchScore} %',
                shortDate(a.createdAt),
                if (a.message != null) '\n« ${a.message} »',
              ].join(' · ')),
              isThreeLine: a.message != null,
              onTap: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => CandidateDetailScreen(profileId: a.candidateId),
              )),
              trailing: PopupMenuButton<String>(
                tooltip: 'Changer le statut',
                onSelected: (s) => _run(() => _svc.setApplicationStatus(a.id, s)),
                itemBuilder: (_) => [
                  for (final e in OfferLabels.applicationStatuses.entries)
                    if (e.key != 'SENT')
                      PopupMenuItem(value: e.key, child: Text(e.value)),
                ],
                child: StatusChip(
                  status: a.status,
                  label: OfferLabels.applicationStatuses[a.status] ?? a.status,
                ),
              ),
            ),
          );
        },
      );
    },
  );

  // ---------------- Onglet Correspondants ----------------
  Widget _matchesTab() => FutureBuilder<List<CandidateSummary>>(
    future: _matches,
    builder: (context, snap) {
      if (snap.connectionState != ConnectionState.done) {
        return const Center(child: CircularProgressIndicator());
      }
      if (snap.hasError) return Center(child: Text('Erreur : ${snap.error}'));
      final list = snap.data!;
      if (list.isEmpty) {
        return const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'Aucun candidat correspondant.\nSeuls les candidats qui ont autorisé '
              'les recruteurs et dont le CV est confirmé apparaissent ici.',
              textAlign: TextAlign.center,
            ),
          ),
        );
      }
      return ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: list.length,
        itemBuilder: (_, i) {
          final c = list[i];
          return Card(
            child: ListTile(
              leading: CircleAvatar(child: Text('${c.matchScore}%')),
              title: Text(c.fullName),
              subtitle: Text([
                if (c.sector != null) c.sector!,
                if (c.region != null) c.region!,
                'Compétences : ${c.matchedSkills.join(', ')}',
                if (c.missingSkills.isNotEmpty) 'Manque : ${c.missingSkills.join(', ')}',
              ].join('\n')),
              isThreeLine: true,
              onTap: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => CandidateDetailScreen(profileId: c.profileId),
              )),
            ),
          );
        },
      );
    },
  );
}
