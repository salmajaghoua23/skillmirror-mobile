import 'dart:async';
import 'package:flutter/material.dart';
import '../services/progress_api.dart';

class AnalysisProgressBar extends StatefulWidget {
  final String title;
  final Future<AnalysisProgress> Function() poll;
  final VoidCallback onFinished;   // DONE ou UNKNOWN : recharger les données depuis la base
  final VoidCallback? onRetry;
  final bool indeterminate;        // true pour l'analyse du métier (pas d'étapes fines)

  const AnalysisProgressBar({
    super.key,
    required this.title,
    required this.poll,
    required this.onFinished,
    this.onRetry,
    this.indeterminate = false,
  });

  @override
  State<AnalysisProgressBar> createState() => _AnalysisProgressBarState();
}

class _AnalysisProgressBarState extends State<AnalysisProgressBar> {
  Timer? _timer;
  AnalysisProgress? _p;
  bool _finished = false;

  @override
  void initState() {
    super.initState();
    _tick();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _tick());
  }

  Future<void> _tick() async {
    try {
      final p = await widget.poll();
      if (!mounted) return;
      setState(() => _p = p);
      if ((p.status == 'DONE' || p.status == 'UNKNOWN') && !_finished) {
        _finished = true;
        _timer?.cancel();
        widget.onFinished();
      } else if (p.status == 'FAILED') {
        _timer?.cancel();
      }
    } catch (_) {
      // réseau instable : on réessaie au prochain tick
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final p = _p;

    if (p != null && p.status == 'FAILED') {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(widget.title,
                style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            Text(p.message, style: const TextStyle(color: Colors.red)),
            if (widget.onRetry != null)
              TextButton(
                  onPressed: widget.onRetry, child: const Text('Réessayer')),
          ]),
        ),
      );
    }

    final waiting = p == null || p.status == 'QUEUED';
    final double? value =
    (widget.indeterminate || waiting) ? null : p.percent / 100;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(widget.title,
              style: const TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          LinearProgressIndicator(value: value),
          const SizedBox(height: 6),
          Text(waiting
              ? 'En attente…'
              : widget.indeterminate
              ? p.message
              : '${p.message} (${p.percent} %)'),
          const SizedBox(height: 2),
          const Text("Tu peux continuer à utiliser l'application.",
              style: TextStyle(fontSize: 12, color: Colors.grey)),
        ]),
      ),
    );
  }
}