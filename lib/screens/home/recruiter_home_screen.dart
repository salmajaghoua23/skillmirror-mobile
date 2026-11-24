import 'package:flutter/material.dart';

import '../../models/job_offer.dart';
import '../../services/auth_service.dart';
import '../../services/recruiter_offer_service.dart';
import '../../widgets/status_chip.dart';
import '../recruiter/candidate_detail_screen.dart';
import '../recruiter/offer_form_screen.dart';
import '../recruiter/offers_screen.dart';
import '../recruiter/recruiter_profile_screen.dart';
import '../settings_screen.dart';

/// Dashboard recruteur (Phase 7).
class RecruiterHomeScreen extends StatefulWidget {
  const RecruiterHomeScreen({super.key});

  @override
  State<RecruiterHomeScreen> createState() => _RecruiterHomeScreenState();
}

class _RecruiterHomeScreenState extends State<RecruiterHomeScreen> {
  late Future<RecruiterDashboard> _future = RecruiterOfferService().dashboard();

  void _reload() => setState(() {
    _future = RecruiterOfferService().dashboard();
  });
  Future<void> _open(Widget screen) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
    if (mounted) _reload();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SkillMirror – Recruteur'),
        actions: [
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            tooltip: 'Mon profil',
            onPressed: () => _open(const RecruiterProfileScreen()),
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Apparence',
            onPressed: () => _open(const SettingsScreen()),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => AuthService().signOut(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _open(const OfferFormScreen()),
        icon: const Icon(Icons.add),
        label: const Text('Nouvelle offre'),
      ),
      body: FutureBuilder<RecruiterDashboard>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text('Erreur : ${snap.error}', textAlign: TextAlign.center),
                    const SizedBox(height: 8),
                    TextButton(onPressed: _reload, child: const Text('Réessayer')),
                  ],
                ),
              ),
            );
          }
          return RefreshIndicator(
            onRefresh: () async => _reload(),
            child: _content(snap.data!),
          );
        },
      ),
    );
  }

  Widget _content(RecruiterDashboard d) {
    final t = Theme.of(context).textTheme;
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 96),
      children: [
        Text('Bonjour ${d.fullName} 👋',
            style: t.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(d.companyName, style: t.bodyMedium),
        const SizedBox(height: 20),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.7,
          children: [
            _stat('Offres publiées', d.publishedOffers, Icons.campaign_outlined),
            _stat('Candidatures', d.totalApplications, Icons.people_outline),
            _stat('Nouvelles', d.newApplications, Icons.mark_email_unread_outlined),
            _stat('Acceptées', d.accepted, Icons.check_circle_outline),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          '${d.draftOffers} brouillon(s) · ${d.closedOffers} clôturée(s) · '
              '${d.shortlisted} présélectionnée(s) · ${d.rejected} refusée(s)',
          style: t.bodySmall,
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          icon: const Icon(Icons.work_outline),
          label: const Text('Mes offres'),
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          onPressed: () => _open(const OffersScreen()),
        ),
        const SizedBox(height: 24),
        Text('Dernières candidatures',
            style: t.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        if (d.recentApplications.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Text('Aucune candidature pour le moment.'),
          ),
        for (final a in d.recentApplications)
          Card(
            child: ListTile(
              title: Text(a.candidateName),
              subtitle: Text('${a.offerTitle ?? ''} · ${shortDate(a.createdAt)}'),
              trailing: StatusChip(
                status: a.status,
                label: OfferLabels.applicationStatuses[a.status] ?? a.status,
              ),
              onTap: () => _open(CandidateDetailScreen(profileId: a.candidateId)),
            ),
          ),
      ],
    );
  }

  Widget _stat(String label, int value, IconData icon) => Card(
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Icon(icon, size: 18),
            const SizedBox(width: 6),
            Expanded(child: Text(label, overflow: TextOverflow.ellipsis)),
          ]),
          const SizedBox(height: 6),
          Text('$value',
              style: Theme.of(context)
                  .textTheme
                  .headlineMedium
                  ?.copyWith(fontWeight: FontWeight.w800)),
        ],
      ),
    ),
  );
}
