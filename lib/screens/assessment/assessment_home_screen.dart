import 'package:flutter/material.dart';

import '../../models/assessment.dart';
import '../../services/assessment_service.dart';
import 'quiz_screen.dart';
import 'result_screen.dart';

/// Tests de compétences : choisir un test, et consulter l'historique.
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

  void _reloadHistory() => setState(() {
    _history = _svc.history();
  });
  void _toast(String m) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(m)));

  Future<String?> _chooseLevel(String skill) => showModalBottomSheet<String>(
    context: context,
    showDragHandle: true,
    builder: (ctx) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              'Niveau du test : $skill',
              style: Theme.of(ctx).textTheme.titleMedium,
            ),
          ),
          for (final e in AssessmentLabels.levels.entries)
            ListTile(
              leading: const Icon(Icons.bar_chart),
              title: Text(e.value),
              subtitle: Text(AssessmentLabels.levelHints[e.key] ?? ''),
              onTap: () => Navigator.pop(ctx, e.key),
            ),
          const SizedBox(height: 8),
        ],
      ),
    ),
  );

  Future<void> _start(AvailableSkill s) async {
    final level = await _chooseLevel(s.name);
    if (level == null || !mounted) return;
    setState(() => _starting = true);
    try {
      final session = await _svc.start(s.skillId, level);
      if (!mounted) return;
      setState(() => _starting = false);
      await Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => QuizScreen(session: session)),
      );
      if (mounted) _reloadHistory();
    } catch (e) {
      if (!mounted) return;
      setState(() => _starting = false);
      _toast(e.toString());
    }
  }

  static Color _scoreColor(double s) =>
      s >= 70 ? Colors.green : (s >= 50 ? Colors.orange : Colors.red);

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Tests de compétences'),
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
      return ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: list.length,
        itemBuilder: (_, i) {
          final s = list[i];
          return Card(
            child: ListTile(
              leading: const CircleAvatar(child: Icon(Icons.quiz_outlined)),
              title: Text(s.name, style: const TextStyle(fontWeight: FontWeight.w700)),
              subtitle: Text('${s.category ?? ''} · ${s.questionCount} question(s)'),
              trailing: const Icon(Icons.play_arrow),
              onTap: () => _start(s),
            ),
          );
        },
      );
    },
  );

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
        onRefresh: () async => _reloadHistory(),
        child: ListView.builder(
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
                  '$date · ${h.total ?? 0} question(s)'
                      '${done ? '' : ' · non terminé'}',
                ),
                trailing: done ? const Icon(Icons.chevron_right) : null,
                onTap: done
                    ? () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => ResultScreen(assessmentId: h.id),
                  ),
                )
                    : () => _toast("Ce test n'a pas été terminé."),
              ),
            );
          },
        ),
      );
    },
  );
}