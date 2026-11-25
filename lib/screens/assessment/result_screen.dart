import 'package:flutter/material.dart';

import '../../models/assessment.dart';
import '../../services/assessment_service.dart';

/// Résultat d'un test : score, suite possible (niveau suivant / refaire / tableau de bord)
/// et correction question par question.
class ResultScreen extends StatefulWidget {
  final String assessmentId;
  const ResultScreen({super.key, required this.assessmentId});

  @override
  State<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends State<ResultScreen> {
  late final Future<AssessmentResult> _future =
  AssessmentService().result(widget.assessmentId);

  void _toDashboard() =>
      Navigator.of(context).popUntil((route) => route.isFirst);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Résultat du test')),
      body: FutureBuilder<AssessmentResult>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) return Center(child: Text('Erreur : ${snap.error}'));
          return _body(snap.data!);
        },
      ),
    );
  }

  Widget _body(AssessmentResult r) {
    final t = Theme.of(context).textTheme;
    final color = r.passed
        ? Colors.green
        : (r.score >= 40 ? Colors.orange : Colors.red);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                Text(
                  '${r.skill} · ${AssessmentLabels.levels[r.level] ?? r.level}',
                  style: t.titleMedium,
                ),
                const SizedBox(height: 12),
                Text(
                  '${r.score.toStringAsFixed(0)} %',
                  style: t.displaySmall?.copyWith(
                    fontWeight: FontWeight.w800,
                    color: color,
                  ),
                ),
                Text('${r.correctCount} / ${r.total} bonnes réponses'),
                const SizedBox(height: 10),
                Chip(
                  backgroundColor: color.withValues(alpha: 0.15),
                  label: Text(
                    r.passed ? 'Niveau validé ✓' : 'À retravailler',
                    style: TextStyle(color: color, fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        _actions(r),
        const SizedBox(height: 20),
        Text('Correction', style: t.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        for (var i = 0; i < r.questions.length; i++) _questionCard(i, r.questions[i]),
      ],
    );
  }

  Widget _actions(AssessmentResult r) {
    final next = r.nextLevel;
    final nextLabel = next == null ? '' : (AssessmentLabels.levels[next] ?? next);
    final mark = r.passMark.toStringAsFixed(0);
    final progress = '${r.listsDone}/${r.nbLists} listes terminées · '
        'score cumulé ${r.levelScore.toStringAsFixed(0)} %';

    String message;
    if (!r.levelFinished) {
      message = 'Liste ${r.listNo} terminée. $progress. '
          'Terminez les ${r.nbLists} listes pour débloquer la suite.';
    } else if (r.levelPassed && next != null) {
      message = 'Niveau validé ! $progress. Le niveau $nextLabel est débloqué.';
    } else if (r.levelPassed) {
      message = 'Bravo ! Vous avez validé tous les niveaux de cette compétence.';
    } else {
      message = 'Score global insuffisant ($progress). Il faut $mark % minimum.';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LinearProgressIndicator(value: r.listsDone / r.nbLists),
        const SizedBox(height: 10),
        Text(message, textAlign: TextAlign.center),
        const SizedBox(height: 12),
        if (!r.levelFinished)
          FilledButton.icon(
            onPressed: () => Navigator.of(context).pop(NextTest(r.skillId, r.level)),
            icon: const Icon(Icons.play_arrow),
            label: Text('Liste suivante (${r.listsDone + 1}/${r.nbLists})'),
          ),
        if (r.levelFinished && r.levelPassed && next != null)
          FilledButton.icon(
            onPressed: () => Navigator.of(context).pop(NextTest(r.skillId, next)),
            icon: const Icon(Icons.lock_open),
            label: Text('Passer au niveau $nextLabel'),
          ),
        if (r.levelFinished && !r.levelPassed)
          FilledButton.icon(
            onPressed: () => Navigator.of(context)
                .pop(NextTest(r.skillId, r.level, restart: true)),
            icon: const Icon(Icons.refresh),
            label: const Text('Refaire le niveau'),
          ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _toDashboard,
          icon: const Icon(Icons.dashboard_outlined),
          label: Text(r.levelFinished ? 'Aller au tableau de bord' : 'Continuer plus tard'),
        ),
      ],
    );
  }
  Widget _questionCard(int i, ResultQuestion q) {
    final t = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  q.correct ? Icons.check_circle : Icons.cancel,
                  color: q.correct ? Colors.green : Colors.red,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '${i + 1}. ${q.text}',
                    style: t.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            for (var k = 0; k < q.options.length; k++) _option(q, k),
            if (q.selectedIndex == null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text('Question non répondue', style: t.bodySmall),
              ),
            if (q.explanation != null && q.explanation!.isNotEmpty)
              Container(
                margin: const EdgeInsets.only(top: 10),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.blue.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text('💡 ${q.explanation}'),
              ),
          ],
        ),
      ),
    );
  }

  Widget _option(ResultQuestion q, int k) {
    final isCorrect = k == q.correctIndex;
    final isSelected = k == q.selectedIndex;
    Color? bg;
    IconData icon = Icons.circle_outlined;
    Color iconColor = Colors.grey;
    if (isCorrect) {
      bg = Colors.green.withValues(alpha: 0.15);
      icon = Icons.check_circle;
      iconColor = Colors.green;
    } else if (isSelected) {
      bg = Colors.red.withValues(alpha: 0.15);
      icon = Icons.cancel;
      iconColor = Colors.red;
    }
    return Container(
      margin: const EdgeInsets.only(top: 6),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)),
      child: Row(
        children: [
          Icon(icon, size: 18, color: iconColor),
          const SizedBox(width: 8),
          Expanded(child: Text(q.options[k])),
        ],
      ),
    );
  }
}