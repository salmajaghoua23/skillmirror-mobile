import 'package:flutter/material.dart';

import '../../models/assessment.dart';
import '../../services/assessment_service.dart';

/// Résultat d'un test : score, correction question par question et explications.
class ResultScreen extends StatefulWidget {
  final String assessmentId;
  const ResultScreen({super.key, required this.assessmentId});

  @override
  State<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends State<ResultScreen> {
  late final Future<AssessmentResult> _future =
  AssessmentService().result(widget.assessmentId);

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
    final color = r.passed ? Colors.green : (r.score >= 50 ? Colors.orange : Colors.red);

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
        Text('Correction', style: t.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        for (var i = 0; i < r.questions.length; i++) _questionCard(i, r.questions[i]),
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