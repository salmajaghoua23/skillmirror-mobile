import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../models/candidate_cv.dart';
import '../../services/candidate_cv_service.dart';
import 'cv_analysis_screen.dart';

class CvScreen extends StatefulWidget {
  const CvScreen({super.key});
  @override
  State<CvScreen> createState() => _CvScreenState();
}

class _CvScreenState extends State<CvScreen> {
  final _service = CandidateCvService();
  late Future<List<CandidateCv>> _future;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = _service.list();
  }

  void _reload() {
    setState(() {
      _future = _service.list();
    });
  }

  void _msg(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  String _size(int bytes) {
    if (bytes < 1024) return '$bytes o';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} Ko';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} Mo';
  }

  Future<void> _add() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf', 'docx'],
    );
    final file = result?.files.single;
    if (file == null || file.path == null) return;

    setState(() => _busy = true);
    CandidateCv? uploaded;
    try {
      uploaded = await _service.upload(file.path!, file.name);
      _msg('CV ajouté');
      _reload();
    } catch (e) {
      _msg("Échec de l'envoi : $e");
    }finally {
      if (mounted) setState(() => _busy = false);
    }

    if (uploaded == null || !mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => CvAnalysisScreen(cvId: uploaded!.id.toString())),
    );
    _reload();
  }


  Future<void> _setActive(CandidateCv cv) async {
    try {
      await _service.setActive(cv.id);
      _reload();
    } catch (_) {
      _msg('Erreur');
    }
  }

  Future<void> _delete(CandidateCv cv) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Supprimer ce CV ?'),
        content: Text(cv.fileName),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuler'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Supprimer'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await _service.delete(cv.id);
      _reload();
    } catch (_) {
      _msg('Erreur lors de la suppression');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mes CV')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _busy ? null : _add,
        icon: _busy
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.upload_file),
        label: const Text('Ajouter un CV'),
      ),
      body: FutureBuilder<List<CandidateCv>>(
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
                  const Text('Impossible de charger les CV'),
                  TextButton(
                    onPressed: _reload,
                    child: const Text('Réessayer'),
                  ),
                ],
              ),
            );
          }
          final cvs = snap.data ?? [];
          if (cvs.isEmpty) {
            return const Center(
              child: Text('Aucun CV. Ajoutez votre premier CV.'),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.only(bottom: 88),
            itemCount: cvs.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) {
              final cv = cvs[i];
              return ListTile(
                leading: Icon(
                  Icons.description,
                  color: cv.active
                      ? Theme.of(context).colorScheme.primary
                      : Colors.grey,
                ),
                title: Text(
                  cv.fileName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: Text(
                  cv.active
                      ? 'CV actif · ${_size(cv.sizeBytes)}'
                      : _size(cv.sizeBytes),
                ),
                trailing: PopupMenuButton<String>(
                  onSelected: (v) =>
                      v == 'active' ? _setActive(cv) : _delete(cv),
                  itemBuilder: (_) => [
                    if (!cv.active)
                      const PopupMenuItem(
                        value: 'active',
                        child: Text('Définir comme actif'),
                      ),
                    const PopupMenuItem(
                      value: 'delete',
                      child: Text('Supprimer'),
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}
