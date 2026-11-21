import 'package:flutter/material.dart';
import '../../services/cv_analysis_service.dart';

class CvAnalysisScreen extends StatefulWidget {
  final String cvId;
  const CvAnalysisScreen({super.key, required this.cvId});

  @override
  State<CvAnalysisScreen> createState() => _CvAnalysisScreenState();
}

class _CvAnalysisScreenState extends State<CvAnalysisScreen> {
  final _service = CvAnalysisService();
  bool _loading = true;
  String? _error;
  String _status = 'PENDING';
  Map<String, dynamic> _data = {};

  @override
  void initState() {
    super.initState();
    _load(runIfMissing: true);
  }

  Future<void> _load({bool runIfMissing = false, bool force = false}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      var r = force ? await _service.run(widget.cvId) : await _service.get(widget.cvId);
      if (!force && runIfMissing && r['status'] == 'PENDING') {
        r = await _service.run(widget.cvId);
      }
      _apply(r);
    } catch (e) {
      _status = 'FAILED';
      _error = e.toString().replaceFirst('Exception: ', '');
    }
    if (mounted) setState(() => _loading = false);
  }

  void _apply(Map<String, dynamic> r) {
    _status = r['status'] ?? 'PENDING';
    _data = Map<String, dynamic>.from(r['data'] ?? {});
    _error = _status == 'FAILED' ? (r['errorMessage'] ?? 'Échec de l\'extraction.') : null;
  }

  List<dynamic> _list(String key) => List<dynamic>.from(_data[key] ?? []);

  Future<void> _save() async {
    try {
      final r = await _service.update(widget.cvId, _data);
      setState(() => _apply(r));
      _snack('Modifications enregistrées');
    } catch (e) {
      _snack(e.toString());
    }
  }

  Future<void> _confirm() async {
    try {
      await _service.update(widget.cvId, _data); // sauvegarde les corrections avant
      final r = await _service.confirm(widget.cvId);
      setState(() => _apply(r));
      _snack('Informations confirmées');
    } catch (e) {
      _snack(e.toString());
    }
  }

  void _snack(String m) => ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: Text(m.replaceFirst('Exception: ', ''))));

  @override
  Widget build(BuildContext context) {
    final confirmed = _status == 'CONFIRMED';
    return Scaffold(
      appBar: AppBar(title: const Text('Analyse de mon CV'), actions: [
        IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Relancer l\'analyse',
            onPressed: _loading ? null : () => _load(force: true)),
      ]),
      body: _loading
          ? const Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            CircularProgressIndicator(),
            SizedBox(height: 12),
            Text('Analyse du CV en cours...'),
          ]))
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
                    onPressed: () => _load(force: true), child: const Text('Réessayer')),
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
                  padding: const EdgeInsets.all(12), child: Text(_data['summary']))),
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
              child: OutlinedButton(onPressed: _save, child: const Text('Enregistrer'))),
          const SizedBox(width: 12),
          Expanded(child: FilledButton(onPressed: _confirm, child: const Text('Confirmer'))),
        ]),
      ]),
    );
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