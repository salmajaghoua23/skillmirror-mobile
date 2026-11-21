import 'package:flutter/material.dart';
import '../../services/cv_analysis_service.dart';
import 'cv_analysis_screen.dart';

class CvInsightsCard extends StatefulWidget {
  const CvInsightsCard({super.key});

  @override
  State<CvInsightsCard> createState() => _CvInsightsCardState();
}

class _CvInsightsCardState extends State<CvInsightsCard> {
  final _service = CvAnalysisService();
  bool _loading = true;
  String? _error;
  Map<String, dynamic>? _analysis;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _analysis = await _service.latest();
    } catch (e) {
      _error = e.toString().replaceFirst('Exception: ', '');
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _open() async {
    final id = _analysis?['cvId'];
    if (id == null) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => CvAnalysisScreen(cvId: id.toString())),
    );
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    if (_loading) {
      return _shell(cs, child: const Padding(
        padding: EdgeInsets.symmetric(vertical: 32),
        child: Center(child: CircularProgressIndicator()),
      ));
    }
    if (_error != null) {
      return _message(cs,
          icon: Icons.cloud_off_outlined,
          color: cs.error,
          title: 'Analyse indisponible',
          subtitle: _error!,
          action: TextButton(onPressed: _load, child: const Text('Réessayer')));
    }
    if (_analysis == null) {
      return _message(cs,
          icon: Icons.upload_file_outlined,
          color: cs.primary,
          title: 'Aucun CV importé',
          subtitle: 'Importe ton CV pour découvrir tes compétences détectées.');
    }

    final status = _analysis!['status'] as String? ?? 'PENDING';
    if (status == 'FAILED') {
      return _message(cs,
          icon: Icons.error_outline,
          color: cs.error,
          title: 'L\'analyse du CV a échoué',
          subtitle: _analysis!['errorMessage'] ?? 'Ouvre l\'analyse pour réessayer.',
          action: TextButton(onPressed: _open, child: const Text('Ouvrir')));
    }
    if (status == 'PENDING' || status == 'PROCESSING') {
      return _message(cs,
          icon: Icons.hourglass_top_outlined,
          color: cs.tertiary,
          title: 'Analyse en attente',
          subtitle: 'Ton CV n\'a pas encore été analysé.',
          action: TextButton(onPressed: _open, child: const Text('Analyser')));
    }

    final data = Map<String, dynamic>.from(_analysis!['data'] ?? {});
    final tech = List<String>.from(data['technicalSkills'] ?? []);
    final soft = List<String>.from(data['softSkills'] ?? []);
    int count(String k) => (data[k] as List?)?.length ?? 0;
    final summary = (data['summary'] ?? '').toString();
    final simplified = summary.startsWith('Analyse automatique simplifiée');
    final confirmed = status == 'CONFIRMED';

    return _shell(
      cs,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // En-tête
        Row(children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: cs.primaryContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(Icons.insights_rounded, color: cs.onPrimaryContainer),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Profil détecté dans ton CV',
                  style: Theme.of(context)
                      .textTheme
                      .titleMedium
                      ?.copyWith(fontWeight: FontWeight.w700)),
              Text('${tech.length} compétences techniques',
                  style: Theme.of(context)
                      .textTheme
                      .bodySmall
                      ?.copyWith(color: cs.onSurfaceVariant)),
            ]),
          ),
          _badge(cs, confirmed),
        ]),

        if (simplified) ...[
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: cs.tertiaryContainer.withOpacity(0.6),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(children: [
              Icon(Icons.info_outline, size: 18, color: cs.onTertiaryContainer),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Analyse simplifiée : vérifie et corrige les informations.',
                  style: TextStyle(fontSize: 12.5, color: cs.onTertiaryContainer),
                ),
              ),
            ]),
          ),
        ] else if (summary.isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(summary,
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: cs.onSurfaceVariant, height: 1.4)),
        ],

        const SizedBox(height: 18),
        // Statistiques
        Row(children: [
          _stat(cs, Icons.school_outlined, count('education'), 'Formations'),
          _stat(cs, Icons.work_outline, count('experiences'), 'Expériences'),
          _stat(cs, Icons.translate, count('languages'), 'Langues'),
          _stat(cs, Icons.workspace_premium_outlined, count('certifications'), 'Certifs'),
        ]),

        const SizedBox(height: 20),
        _sectionTitle(context, 'Compétences techniques'),
        const SizedBox(height: 8),
        tech.isEmpty
            ? Text('Aucune détectée',
            style: TextStyle(color: cs.onSurfaceVariant))
            : Wrap(spacing: 6, runSpacing: 6, children: [
          for (final s in tech.take(8)) _chip(cs, s),
          if (tech.length > 8) _chip(cs, '+${tech.length - 8}', muted: true),
        ]),

        if (soft.isNotEmpty) ...[
          const SizedBox(height: 18),
          _sectionTitle(context, 'Compétences comportementales'),
          const SizedBox(height: 8),
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final s in soft.take(6)) _chip(cs, s, secondary: true),
          ]),
        ],

        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: _open,
            icon: Icon(confirmed ? Icons.edit_outlined : Icons.fact_check_outlined, size: 18),
            label: Text(confirmed ? 'Voir ou modifier' : 'Vérifier et confirmer'),
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ),
      ]),
    );
  }

  // ---------- éléments de design ----------

  Widget _shell(ColorScheme cs, {required Widget child}) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: cs.surface,
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: cs.outlineVariant.withOpacity(0.6)),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withOpacity(0.04),
          blurRadius: 16,
          offset: const Offset(0, 6),
        ),
      ],
    ),
    child: child,
  );

  Widget _message(ColorScheme cs,
      {required IconData icon,
        required Color color,
        required String title,
        required String subtitle,
        Widget? action}) =>
      _shell(
        cs,
        child: Row(children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: color.withOpacity(0.12),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 2),
              Text(subtitle,
                  style: TextStyle(fontSize: 13, color: cs.onSurfaceVariant)),
            ]),
          ),
          if (action != null) action,
        ]),
      );

  Widget _badge(ColorScheme cs, bool confirmed) {
    final color = confirmed ? Colors.green : Colors.orange;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(confirmed ? Icons.verified : Icons.schedule, size: 14, color: color),
        const SizedBox(width: 4),
        Text(confirmed ? 'Confirmé' : 'À confirmer',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color)),
      ]),
    );
  }

  Widget _stat(ColorScheme cs, IconData icon, int value, String label) => Expanded(
    child: Container(
      margin: const EdgeInsets.symmetric(horizontal: 3),
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest.withOpacity(0.5),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(children: [
        Icon(icon, size: 20, color: cs.primary),
        const SizedBox(height: 6),
        Text('$value',
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 2),
        Text(label,
            style: TextStyle(fontSize: 11, color: cs.onSurfaceVariant),
            overflow: TextOverflow.ellipsis),
      ]),
    ),
  );

  Widget _sectionTitle(BuildContext context, String text) => Text(
    text.toUpperCase(),
    style: Theme.of(context).textTheme.labelSmall?.copyWith(
      letterSpacing: 0.8,
      fontWeight: FontWeight.w700,
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    ),
  );

  Widget _chip(ColorScheme cs, String text,
      {bool secondary = false, bool muted = false}) =>
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: muted
              ? cs.surfaceContainerHighest
              : secondary
              ? cs.secondaryContainer.withOpacity(0.7)
              : cs.primaryContainer.withOpacity(0.7),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(text,
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.w500,
              color: muted
                  ? cs.onSurfaceVariant
                  : secondary
                  ? cs.onSecondaryContainer
                  : cs.onPrimaryContainer,
            )),
      );
}