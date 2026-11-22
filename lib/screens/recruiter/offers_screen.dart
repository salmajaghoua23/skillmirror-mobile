import 'package:flutter/material.dart';

import '../../models/job_offer.dart';
import '../../services/recruiter_offer_service.dart';
import '../../widgets/status_chip.dart';
import 'offer_detail_screen.dart';
import 'offer_form_screen.dart';

/// Liste des offres du recruteur (consultation des offres publiées, brouillons, clôturées).
class OffersScreen extends StatefulWidget {
  const OffersScreen({super.key});

  @override
  State<OffersScreen> createState() => _OffersScreenState();
}

class _OffersScreenState extends State<OffersScreen> {
  String? _filter; // null = toutes
  late Future<List<JobOffer>> _future = RecruiterOfferService().offers();

  void _reload() =>
      setState(() => _future = RecruiterOfferService().offers(status: _filter));

  Future<void> _open(Widget screen) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => screen));
    if (mounted) _reload();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mes offres')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _open(const OfferFormScreen()),
        icon: const Icon(Icons.add),
        label: const Text('Nouvelle offre'),
      ),
      body: Column(
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
            child: Row(
              children: [
                _chip('Toutes', null),
                for (final e in OfferLabels.offerStatuses.entries)
                  _chip(e.value, e.key),
              ],
            ),
          ),
          Expanded(
            child: FutureBuilder<List<JobOffer>>(
              future: _future,
              builder: (context, snap) {
                if (snap.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snap.hasError) {
                  return Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('Erreur : ${snap.error}', textAlign: TextAlign.center),
                        TextButton(onPressed: _reload, child: const Text('Réessayer')),
                      ],
                    ),
                  );
                }
                final list = snap.data!;
                if (list.isEmpty) {
                  return const Center(child: Text('Aucune offre.'));
                }
                return RefreshIndicator(
                  onRefresh: () async => _reload(),
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
                    itemCount: list.length,
                    itemBuilder: (_, i) => _card(list[i]),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _chip(String label, String? value) => Padding(
    padding: const EdgeInsets.only(right: 8),
    child: ChoiceChip(
      label: Text(label),
      selected: _filter == value,
      onSelected: (_) {
        _filter = value;
        _reload();
      },
    ),
  );

  Widget _card(JobOffer o) {
    final info = [
      OfferLabels.contractTypes[o.contractType] ?? o.contractType,
      if (o.location != null) o.location!,
      if (o.remote) 'Télétravail',
    ].join(' · ');
    return Card(
      child: ListTile(
        title: Text(o.title, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text('$info\n${o.skills.length} compétence(s) · '
              '${o.applicationsCount} candidature(s)'),
        ),
        isThreeLine: true,
        trailing: StatusChip(
          status: o.status,
          label: OfferLabels.offerStatuses[o.status] ?? o.status,
        ),
        onTap: () => _open(OfferDetailScreen(offerId: o.id)),
      ),
    );
  }
}
