import 'package:flutter/material.dart';

import '../../models/assessment.dart';
import '../../services/assessment_service.dart';

/// Passage du test : une question à la fois.
class QuizScreen extends StatefulWidget {
  final QuizSession session;
  const QuizScreen({super.key, required this.session});

  @override
  State<QuizScreen> createState() => _QuizScreenState();
}

class _QuizScreenState extends State<QuizScreen> {
  final _svc = AssessmentService();
  int _index = 0;
  final Map<String, int> _answers = {}; // questionId -> option choisie
  bool _submitting = false;

  List<QuizQuestion> get _questions => widget.session.questions;
  QuizQuestion get _q => _questions[_index];

  Future<void> _finish() async {
    final unanswered = _questions.length - _answers.length;
    if (unanswered > 0) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Terminer le test ?'),
          content: Text(
            '$unanswered question(s) sans réponse seront comptées fausses.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Continuer'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Terminer'),
            ),
          ],
        ),
      );
      if (ok != true) return;
    }
    setState(() => _submitting = true);
    try {
      final r = await _svc.submit(
        widget.session.assessmentId,
        _answers,
        _questions,
      );
      if (!mounted) return;
      Navigator.of(context).pop(r.assessmentId);
    } catch (e) {
      if (!mounted) return;
      setState(() => _submitting = false);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final isLast = _index == _questions.length - 1;
    final selected = _answers[_q.id];

    return Scaffold(
      appBar: AppBar(
        title: Text(
          '${widget.session.skill} · '
              '${AssessmentLabels.levels[widget.session.level] ?? widget.session.level}',
        ),
      ),
      body: Column(
        children: [
          LinearProgressIndicator(value: (_index + 1) / _questions.length),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Question ${_index + 1} / ${_questions.length}',
                  style: t.bodySmall,
                ),
                const SizedBox(height: 8),
                Text(
                  _q.text,
                  style: t.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 16),
                for (var i = 0; i < _q.options.length; i++)
                  Card(
                    color: selected == i
                        ? Theme.of(context).colorScheme.primaryContainer
                        : null,
                    child: ListTile(
                      leading: Icon(
                        selected == i
                            ? Icons.radio_button_checked
                            : Icons.radio_button_unchecked,
                      ),
                      title: Text(_q.options[i]),
                      onTap: _submitting
                          ? null
                          : () => setState(() => _answers[_q.id] = i),
                    ),
                  ),
              ],
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: (_index == 0 || _submitting)
                          ? null
                          : () => setState(() => _index--),
                      child: const Text('Précédent'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: FilledButton(
                      onPressed: _submitting
                          ? null
                          : (isLast ? _finish : () => setState(() => _index++)),
                      child: Text(
                        _submitting
                            ? 'Correction…'
                            : (isLast ? 'Terminer' : 'Suivant'),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}