import 'package:flutter/material.dart';

import '../models/skill_reference.dart';
import '../services/skill_service.dart';

/// chosen = compétences cochées ; known = toutes celles du référentiel.
typedef SkillPickResult = ({Set<String> chosen, Set<String> known});

/// Ouvre un sélecteur de compétences classées par domaine, avec recherche.
Future<SkillPickResult?> showSkillPicker(
    BuildContext context, {
      required Set<String> selected,
    }) {
  return showModalBottomSheet<SkillPickResult>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => _SkillPickerSheet(selected: selected),
  );
}

class _SkillPickerSheet extends StatefulWidget {
  final Set<String> selected;
  const _SkillPickerSheet({required this.selected});

  @override
  State<_SkillPickerSheet> createState() => _SkillPickerSheetState();
}

class _SkillPickerSheetState extends State<_SkillPickerSheet> {
  late final Set<String> _chosen = {...widget.selected};
  final Set<String> _known = {};
  late final Future<List<SkillDomain>> _future = SkillService().domains();
  String _query = '';

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: MediaQuery.of(context).size.height * 0.85,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Rechercher une compétence',
                border: OutlineInputBorder(),
              ),
              onChanged: (v) => setState(() => _query = v.trim().toLowerCase()),
            ),
          ),
          Expanded(
            child: FutureBuilder<List<SkillDomain>>(
              future: _future,
              builder: (context, snap) {
                if (snap.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snap.hasError) {
                  return Center(child: Text('Erreur : ${snap.error}'));
                }
                final domains = snap.data!;
                for (final d in domains) {
                  for (final s in d.skills) {
                    _known.add(s.name);
                  }
                }
                final visible = <Widget>[];
                for (final d in domains) {
                  final skills = d.skills
                      .where((s) => s.name.toLowerCase().contains(_query))
                      .toList();
                  if (skills.isEmpty) continue;
                  visible.add(
                    ExpansionTile(
                      key: ValueKey('${d.id}-${_query.isNotEmpty}'),
                      initiallyExpanded: _query.isNotEmpty,
                      title: Text(d.name),
                      children: [
                        for (final s in skills)
                          CheckboxListTile(
                            dense: true,
                            title: Text(s.name),
                            value: _chosen.contains(s.name),
                            onChanged: (v) => setState(() {
                              if (v == true) {
                                _chosen.add(s.name);
                              } else {
                                _chosen.remove(s.name);
                              }
                            }),
                          ),
                      ],
                    ),
                  );
                }
                if (visible.isEmpty) {
                  return const Center(child: Text('Aucune compétence trouvée.'));
                }
                return ListView(children: visible);
              },
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.pop(
                    context,
                    (chosen: _chosen, known: _known),
                  ),
                  child: Text('Valider (${_chosen.length})'),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}