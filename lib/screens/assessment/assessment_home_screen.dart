import 'package:flutter/material.dart';

import '../../models/assessment.dart';
import '../../services/assessment_service.dart';
import 'quiz_screen.dart';
import 'result_screen.dart';

/// Tests de compétences : niveaux verrouillés, tests adaptés au profil, historique.
class AssessmentHomeScreen extends StatefulWidget {
  const AssessmentHomeScreen({super.key});

  @override
  State<AssessmentHomeScreen> createState() => _AssessmentHomeScreenState();
}

class _AssessmentHomeScreenState extends State<AssessmentHomeScreen> {
  final _svc = AssessmentService();
  late Future<List<AvailableSkill>> _skills = _svc.available();
  late Future<List<HistoryItem>> _history = _svc.history();
  bool _starting = false;

  void _reload() => setState(() {
    _skills = _svc.available();
    _history = _svc.history();
  });

  void _toast(String m) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));

  /// Enchaîne les tests : quiz, résultat, puis (si demandé) niveau suivant ou nouvel essai.
  Future<void> _runTest(String skillId, String level) async {
    String? skill = skillId;
    String? lvl = level;
    while (skill != null && lvl != null) {
      setState(() => _starting = true);
      final QuizSession session;
      try {
        session = await _svc.start(skill, lvl);
      } catch (e) {
        if (mounted) {
          setState(() => _starting = false);
          _toast(e.toString());
        }
        break;
      }
      if (!mounted) return;
      setState(() => _starting = false);

      final assessmentId = await Navigator.of(context).push<String>(
        MaterialPageRoute(builder: (_) => QuizScreen(session: session)),
      );
      if (!mounted) return;
      if (assessmentId == null) break; // test abandonné

      final next = await Navigator.of(context).push<NextTest>(
        MaterialPageRoute(builder: (_) => ResultScreen(assessmentId: assessmentId)),
      );
      if (!mounted) return;
      _reload();
      skill = next?.skillId;
      lvl = next?.level;
    }
    if (mounted) _reload();
  }

  Future<void> _openResult(String id) async {
    final next = await Navigator.of(context).push<NextTest>(
      MaterialPageRoute(builder: (_) => ResultScreen(assessmentId: id)),
    );
    if (!mounted) return;
    if (next != null) {
      _runTest(next.skillId, next.level);
    }
  }

  void _onLevelTap(AvailableSkill s, LevelStatus l) {
    if (l.locked) {
      _toast('Niveau verrouillé : obtenez au moins 60 % au niveau précédent.');
    } else if (!l.hasQuestions) {
      _toast('Les questions de ce niveau arrivent bientôt.');
    } else {
      _runTest(s.skillId, l.level);
    }
  }

  static Color _scoreColor(double s) =>
      s >= 60 ? Colors.green : (s >= 40 ? Colors.orange : Colors.red);

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Tests de compétences'),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Actualiser',
              onPressed: _reload,
            ),
          ],
          bottom: const TabBar(
            tabs: [Tab(text: 'Passer un test'), Tab(text: 'Historique')],
          ),
        ),
        body: Stack(
          children: [
            TabBarView(children: [_skillsTab(), _historyTab()]),
            if (_starting)
              Container(
                color: Colors.black54,
                child: const Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 12),
                      Text(
                        'Préparation du test…',
                        style: TextStyle(color: Colors.white),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  // ---------------- Onglet « Passer un test » ----------------
  Widget _skillsTab() => FutureBuilder<List<AvailableSkill>>(
    future: _skills,
    builder: (context, snap) {
      if (snap.connectionState != ConnectionState.done) {
        return const Center(child: CircularProgressIndicator());
      }
      if (snap.hasError) return Center(child: Text('Erreur : ${snap.error}'));
      final list = snap.data!;
      if (list.isEmpty) {
        return const Center(child: Text('Aucun test disponible pour le moment.'));
      }
      final recommended = list.where((s) => s.recommended).toList();
      final others = list.where((s) => !s.recommended).toList();
      return RefreshIndicator(
        onRefresh: () async => _reload(),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(12),
          children: [
            _infoCard(
              Icons.lock_open,
              'Réussissez un niveau (60 % minimum) pour débloquer le suivant.',
            ),
            if (recommended.isEmpty)
              _infoCard(
                Icons.person_search_outlined,
                'Importez et confirmez votre CV pour recevoir des tests adaptés à votre profil.',
              ),
            if (recommended.isNotEmpty) ...[
              _sectionTitle('Pour votre profil', "D'après les compétences de votre CV"),
              for (final s in recommended) _skillCard(s),
            ],
            if (others.isNotEmpty) ...[
              _sectionTitle(
                recommended.isEmpty ? 'Tous les tests' : 'Autres compétences',
                null,
              ),
              for (final s in others) _skillCard(s),
            ],
          ],
        ),
      );
    },
  );

  Widget _infoCard(IconData icon, String text) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Icon(icon),
          const SizedBox(width: 10),
          Expanded(child: Text(text)),
        ],
      ),
    ),
  );

  Widget _sectionTitle(String title, String? subtitle) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 14, 4, 6),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context)
              .textTheme
              .titleMedium
              ?.copyWith(fontWeight: FontWeight.w800),
        ),
        if (subtitle != null)
          Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
  );

  Widget _skillCard(AvailableSkill s) => Card(
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  s.name,
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
                ),
              ),
              if (s.recommended)
                const Chip(
                  label: Text('Votre profil'),
                  visualDensity: VisualDensity.compact,
                ),
            ],
          ),
          if (s.category != null)
            Text(s.category!, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [for (final l in s.levels) _levelChip(s, l)],
          ),
        ],
      ),
    ),
  );

  Widget _levelChip(AvailableSkill s, LevelStatus l) {
    final label = AssessmentLabels.levels[l.level] ?? l.level;
    IconData icon;
    Color color;
    if (l.locked) {
      icon = Icons.lock_outline;
      color = Colors.grey;
    } else if (l.passed) {
      icon = Icons.check_circle;
      color = Colors.green;
    } else if (!l.hasQuestions) {
      icon = Icons.hourglass_empty;
      color = Colors.grey;
    } else {
      icon = Icons.play_circle_outline;
      color = Theme.of(context).colorScheme.primary;
    }
    final suffix = l.bestScore != null ? ' · ${l.bestScore!.toStringAsFixed(0)} %' : '';
    return ActionChip(
      avatar: Icon(icon, size: 18, color: color),
      label: Text('$label$suffix'),
      onPressed: () => _onLevelTap(s, l),
    );
  }

  // ---------------- Onglet « Historique » ----------------
  Widget _historyTab() => FutureBuilder<List<HistoryItem>>(
    future: _history,
    builder: (context, snap) {
      if (snap.connectionState != ConnectionState.done) {
        return const Center(child: CircularProgressIndicator());
      }
      if (snap.hasError) return Center(child: Text('Erreur : ${snap.error}'));
      final list = snap.data!;
      if (list.isEmpty) {
        return const Center(child: Text("Vous n'avez pas encore passé de test."));
      }
      return RefreshIndicator(
        onRefresh: () async => _reload(),
        child: ListView.builder(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(12),
          itemCount: list.length,
          itemBuilder: (_, i) {
            final h = list[i];
            final done = h.status == 'COMPLETED';
            final date = (h.createdAt != null && h.createdAt!.length >= 10)
                ? h.createdAt!.substring(0, 10)
                : '';
            return Card(
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: done
                      ? _scoreColor(h.score ?? 0).withValues(alpha: 0.2)
                      : Colors.grey.withValues(alpha: 0.2),
                  child: Text(
                    done ? '${(h.score ?? 0).toStringAsFixed(0)}%' : '…',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: done ? _scoreColor(h.score ?? 0) : Colors.grey,
                    ),
                  ),
                ),
                title: Text(
                  '${h.skill} · ${AssessmentLabels.levels[h.level] ?? h.level}',
                ),
                subtitle: Text(
                  '$date · ${h.total ?? 0} question(s)${done ? '' : ' · non terminé'}',
                ),
                trailing: done ? const Icon(Icons.chevron_right) : null,
                onTap: done
                    ? () => _openResult(h.id)
                    : () => _toast("Ce test n'a pas été terminé."),
              ),
            );
          },
        ),
      );
    },
  );
}