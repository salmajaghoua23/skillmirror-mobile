import 'package:flutter/material.dart';
import '../../services/cv_analysis_service.dart';
import '../../services/progress_api.dart';
import '../../widgets/analysis_progress_bar.dart';
import '../../core/app_events.dart';
import 'dart:convert';

class CvAnalysisScreen extends StatefulWidget {
  final String cvId;
  const CvAnalysisScreen({super.key, required this.cvId});

  @override
  State<CvAnalysisScreen> createState() => _CvAnalysisScreenState();
}

class _CvAnalysisScreenState extends State<CvAnalysisScreen> {
  final _service = CvAnalysisService();
  final _progressApi = ProgressApi();
  bool _analyzing = false;
  int _runId = 0; // force la recréation de la barre lors d'un « Réessayer »
  bool _loading = true;
  String? _error;
  String _status = 'PENDING';
  Map<String, dynamic> _data = {};
  bool _confirming = false;
  String? _confirmedSnapshot;
  bool _hasConfirmedSnapshot = false;

  @override
  void initState() {
    super.initState();
    _load(runIfMissing: true);
  }

  Future<void> _load({bool runIfMissing = false, bool force = false}) async {
    if (force) {
      await _startAnalysis();
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      // Une analyse tourne déjà (retour sur l'écran) : on reprend la barre
      final p = await _progressApi.cv(widget.cvId);
      if (p.isRunning) {
        if (mounted) {
          setState(() {
            _analyzing = true;
            _loading = false;
          });
        }
        return;
      }

      final r = await _service.get(widget.cvId);
      final st = r['status'];
      // jamais analysé, ou analyse interrompue (redémarrage du serveur)
      if (runIfMissing && (st == 'PENDING' || st == 'PROCESSING')) {
        await _startAnalysis();
        return;
      }
      _apply(r);
    } catch (e) {
      _status = 'FAILED';
      _error = e.toString().replaceFirst('Exception: ', '');
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _startAnalysis() async {
    setState(() {
      _analyzing = true;
      _loading = false;
      _error = null;
      _runId++;
    });
    try {
      await _service.run(widget.cvId); // retourne immédiatement
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _analyzing = false;
        _status = 'FAILED';
        _error = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _onAnalysisFinished() async {
    if (!mounted) return;
    setState(() => _analyzing = false);
    await _load(); // recharge le résultat (EXTRACTED) ou l'erreur (FAILED) depuis la base
  }

  void _apply(Map<String, dynamic> r) {
    _status = r['status'] ?? 'PENDING';

    _data = Map<String, dynamic>.from(r['data'] ?? {});

    _error = _status == 'FAILED'
        ? (r['errorMessage'] ?? 'Échec de l’extraction.')
        : null;

    // Enregistrer la dernière version confirmée
    if (_status == 'CONFIRMED') {
      _confirmedSnapshot = _snapshot(_data);
      _hasConfirmedSnapshot = true;
    }
  }

  List<dynamic> _list(String key) => List<dynamic>.from(_data[key] ?? []);

  Future<void> _save() async {
    if (_hasConfirmedSnapshot && !_canConfirm) {
      _snack('Aucune modification à enregistrer.');
      return;
    }

    try {
      final r = await _service.update(widget.cvId, _data);

      if (!mounted) return;

      setState(() => _apply(r));

      _snack('Modifications enregistrées');
    } catch (e) {
      _snack(e.toString());
    }
  }

  Future<void> _confirm() async {
    if (!_canConfirm || _confirming) return;

    setState(() => _confirming = true);

    try {
      // Enregistrer les modifications uniquement si une confirmation est nécessaire
      await _service.update(widget.cvId, _data);

      await _service.confirm(widget.cvId);

      if (!mounted) return;

      setState(() {
        _status = 'CONFIRMED';

        // La version actuelle devient la nouvelle version confirmée
        _confirmedSnapshot = _snapshot(_data);
        _hasConfirmedSnapshot = true;

        _confirming = false;
      });

      AppEvents.cvConfirmed.value++;

      _snack(
        'Informations confirmées. L’analyse de ton profil continue en arrière-plan.',
      );

      Navigator.of(context).popUntil((route) => route.isFirst);
    } catch (e) {
      if (!mounted) return;

      setState(() => _confirming = false);

      _snack(e.toString());
    }
  }

  void _snack(String m) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(m.replaceFirst('Exception: ', ''))));
  }

  @override
  Widget build(BuildContext context) {
    final confirmed = _status == 'CONFIRMED';
    return Scaffold(
      appBar: AppBar(title: const Text('Analyse de mon CV'), actions: [
        IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Relancer l\'analyse',
            onPressed: (_loading || _analyzing) ? null : () => _load(force: true)),
      ]),
      body: _analyzing
          ? Center(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: AnalysisProgressBar(
            key: ValueKey(_runId),
            title: 'Analyse du CV',
            poll: () => _progressApi.cv(widget.cvId),
            onFinished: _onAnalysisFinished,
            onRetry: _startAnalysis,
          ),
        ),
      )
          : _loading
          ? const Center(child: CircularProgressIndicator())
          : _status == 'FAILED'
          ? Center(
          child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 12),
                Text(_error ?? 'Erreur', textAlign: TextAlign.center),
                const SizedBox(height: 16),
                FilledButton(
                    onPressed: () => _load(force: true),
                    child: const Text('Réessayer')),
              ])))
          : ListView(padding: const EdgeInsets.all(16), children: [
        if (confirmed)
          const Card(
              child: ListTile(
                  leading: Icon(Icons.verified, color: Colors.green),
                  title: Text('Informations confirmées'))),
        if ((_data['summary'] ?? '').toString().isNotEmpty)
          Card(
              child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(_data['summary']))),
        _ChipsEditor(
            title: 'Compétences techniques',
            items: _list('technicalSkills').cast<String>(),
            onChanged: (l) => setState(() => _data['technicalSkills'] = l)),
        _ChipsEditor(
            title: 'Compétences comportementales',
            items: _list('softSkills').cast<String>(),
            onChanged: (l) => setState(() => _data['softSkills'] = l)),
        _ObjectsEditor(
            title: 'Formations et diplômes',
            items: _list('education'),
            keyA: 'degree',
            labelA: 'Diplôme',
            keyB: 'institution',
            labelB: 'Établissement',
            onChanged: (l) => setState(() => _data['education'] = l)),
        _ObjectsEditor(
            title: 'Expériences professionnelles',
            items: _list('experiences'),
            keyA: 'title',
            labelA: 'Poste',
            keyB: 'company',
            labelB: 'Entreprise',
            onChanged: (l) => setState(() => _data['experiences'] = l)),
        _ObjectsEditor(
            title: 'Langues',
            items: _list('languages'),
            keyA: 'name',
            labelA: 'Langue',
            keyB: 'level',
            labelB: 'Niveau',
            onChanged: (l) => setState(() => _data['languages'] = l)),
        _ObjectsEditor(
            title: 'Certifications',
            items: _list('certifications'),
            keyA: 'name',
            labelA: 'Certification',
            keyB: 'issuer',
            labelB: 'Organisme',
            onChanged: (l) => setState(() => _data['certifications'] = l)),
        const SizedBox(height: 16),
        Row(children: [
          Expanded(
              child: OutlinedButton(
                  onPressed: _save, child: const Text('Enregistrer'))),
          const SizedBox(width: 12),
          Expanded(
            child: FilledButton(
              onPressed: (!_canConfirm || _confirming || _loading)
                  ? null
                  : _confirm,
              child: _confirming
                  ? const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                ),
              )
                  : const Text('Confirmer'),
            ),
          ),
        ]),
      ]),
    );
  }
  dynamic _normalizeForComparison(dynamic value) {
    if (value is Map) {
      final keys = value.keys.map((e) => e.toString()).toList()..sort();

      return {
        for (final key in keys)
          key: _normalizeForComparison(value[key]),
      };
    }

    if (value is List) {
      return value.map(_normalizeForComparison).toList();
    }

    return value;
  }

  String _snapshot(Map<String, dynamic> data) {
    return jsonEncode(_normalizeForComparison(data));
  }

  bool get _canConfirm {
    // Première confirmation
    if (!_hasConfirmedSnapshot) return true;

    // Réactivation uniquement si les données ont changé
    return _snapshot(_data) != _confirmedSnapshot;
  }
}

class _ChipsEditor extends StatelessWidget {
  final String title;
  final List<String> items;
  final ValueChanged<List<String>> onChanged;
  const _ChipsEditor({required this.title, required this.items, required this.onChanged});

  @override
  Widget build(BuildContext context) => Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
            IconButton(
                icon: const Icon(Icons.add),
                onPressed: () async {
                  final v = await _ask(context, title, ['Valeur']);
                  if (v != null && v[0].isNotEmpty) onChanged([...items, v[0]]);
                }),
          ]),
          Wrap(spacing: 6, children: [
            for (final s in items)
              Chip(label: Text(s), onDeleted: () => onChanged(items.where((e) => e != s).toList())),
          ]),
        ]),
      ));
}

class _ObjectsEditor extends StatelessWidget {
  final String title, keyA, labelA, keyB, labelB;
  final List<dynamic> items;
  final ValueChanged<List<dynamic>> onChanged;
  const _ObjectsEditor({
    required this.title,
    required this.items,
    required this.keyA,
    required this.labelA,
    required this.keyB,
    required this.labelB,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) => Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
            IconButton(
                icon: const Icon(Icons.add),
                onPressed: () async {
                  final v = await _ask(context, title, [labelA, labelB]);
                  if (v != null && v[0].isNotEmpty) onChanged([...items, {keyA: v[0], keyB: v[1]}]);
                }),
          ]),
          for (int i = 0; i < items.length; i++)
            ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              title: Text('${items[i][keyA] ?? ''}'),
              subtitle: Text('${items[i][keyB] ?? ''}'),
              trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                IconButton(
                    icon: const Icon(Icons.edit, size: 20),
                    onPressed: () async {
                      final v = await _ask(context, title, [labelA, labelB],
                          initial: ['${items[i][keyA] ?? ''}', '${items[i][keyB] ?? ''}']);
                      if (v != null) {
                        final copy = List<dynamic>.from(items);
                        copy[i] = {...Map<String, dynamic>.from(items[i]), keyA: v[0], keyB: v[1]};
                        onChanged(copy);
                      }
                    }),
                IconButton(
                    icon: const Icon(Icons.delete_outline, size: 20),
                    onPressed: () => onChanged(List.from(items)..removeAt(i))),
              ]),
            ),
        ]),
      ));
}

Future<List<String>?> _ask(BuildContext context, String title, List<String> labels,
    {List<String>? initial}) {
  final ctrls = [
    for (int i = 0; i < labels.length; i++)
      TextEditingController(text: initial != null ? initial[i] : '')
  ];
  return showDialog<List<String>>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(title),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          for (int i = 0; i < labels.length; i++)
            TextField(controller: ctrls[i], decoration: InputDecoration(labelText: labels[i])),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuler')),
          FilledButton(
              onPressed: () => Navigator.pop(context, ctrls.map((c) => c.text.trim()).toList()),
              child: const Text('OK')),
        ],
      ));
}