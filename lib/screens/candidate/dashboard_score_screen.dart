import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/score_models.dart';
import '../../providers/score_provider.dart';
import '../../services/progress_api.dart';
import '../../widgets/analysis_progress_bar.dart';

class DashboardScoreScreen extends StatefulWidget {
  final String profileId;

  const DashboardScoreScreen({
    super.key,
    required this.profileId,
  });

  @override
  State<DashboardScoreScreen> createState() =>
      _DashboardScoreScreenState();
}

class _DashboardScoreScreenState
    extends State<DashboardScoreScreen> {

  final _progressApi = ProgressApi();
  bool _goalAnalyzing = false;
  int _goalRun = 0; // force la recréation de la barre lors d'une nouvelle analyse

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) => _ensureGoal());
  }

  /// Demande au backend de s'assurer que l'analyse du métier visé existe.
  /// Si elle tourne en arrière-plan, la barre de progression s'affiche.
  Future<void> _ensureGoal() async {
    final provider = context.read<ScoreProvider>();
    try {
      final p = await _progressApi.ensureGoal(widget.profileId);
      if (!mounted) return;
      if (p.isRunning) {
        setState(() {
          _goalAnalyzing = true;
          _goalRun++;
        });
      }
    } catch (_) {
      // on affiche quand même ce qui existe déjà
    }
    if (mounted) provider.load(widget.profileId);
  }

  void _onGoalFinished() {
    if (!mounted) return;
    setState(() => _goalAnalyzing = false);
    // score, compétences à améliorer et recommandations mis à jour
    context.read<ScoreProvider>().load(widget.profileId);
  }

  Color _prio(String p) =>
      p == 'HIGH'
          ? Colors.red
          : p == 'MEDIUM'
          ? Colors.orange
          : Colors.green;

  Future<void> _refreshAnalysis() async {

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Actualiser l’analyse ?'),
        content: const Text(
          'Une nouvelle analyse de ton CV et de ton objectif professionnel '
              'sera effectuée avec l’IA, en arrière-plan. Les anciennes '
              'recommandations seront remplacées par les nouvelles.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Annuler'),
          ),
          ElevatedButton.icon(
            onPressed: () => Navigator.pop(context, true),
            icon: const Icon(Icons.refresh),
            label: const Text('Actualiser'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    try {
      await _progressApi.refreshGoal(widget.profileId); // retourne immédiatement
      if (!mounted) return;
      setState(() {
        _goalAnalyzing = true;
        _goalRun++;
      });
    } catch (e) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Erreur lors de l’analyse : $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.watch<ScoreProvider>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mon score'),
        actions: [
          IconButton(
            tooltip: 'Relancer l’analyse IA',
            onPressed: (_goalAnalyzing || p.loading)
                ? null
                : _refreshAnalysis,
            icon: const Icon(Icons.refresh),
          ),
          const SizedBox(width: 8),
        ],
      ),

      body: Column(
        children: [
          if (_goalAnalyzing)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: AnalysisProgressBar(
                key: ValueKey(_goalRun),
                title: 'Analyse de ton profil et du métier visé',
                indeterminate: true,
                poll: () => _progressApi.goal(widget.profileId),
                onFinished: _onGoalFinished,
                onRetry: _refreshAnalysis,
              ),
            ),
          Expanded(
            child: p.loading
                ? const Center(child: CircularProgressIndicator())
                : p.error != null
                ? Center(child: Text(p.error!))
                : p.data == null
                ? const SizedBox()
                : RefreshIndicator(
              onRefresh: () => p.load(widget.profileId),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _scoreGauge(p.data!),

                  const SizedBox(height: 16),

                  _goalCard(p.data!),

                  const SizedBox(height: 16),

                  const Text(
                    'Compétences à améliorer',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),

                  ...p.data!.missing.map(_missingTile),

                  const SizedBox(height: 16),

                  const Text(
                    'Recommandations',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),

                  if (p.data!.recommendations.isEmpty)
                    const Card(
                      child: Padding(
                        padding: EdgeInsets.all(16),
                        child: Text(
                          'Aucune recommandation disponible.',
                        ),
                      ),
                    ),

                  ...p.data!.recommendations.map(
                        (r) => Card(
                      child: ListTile(
                        leading: Icon(
                          Icons.lightbulb,
                          color: _prio(r.priority),
                        ),
                        title: Text(r.message),
                        subtitle: Text(
                          '${r.type} • ${r.priority}',
                        ),
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

  Widget _scoreGauge(DashboardDto d) {
    final s = d.score;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            SizedBox(
              width: 140,
              height: 140,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  SizedBox.expand(
                    child: CircularProgressIndicator(
                      value: s.global / 100,
                      strokeWidth: 12,
                    ),
                  ),
                  Text(
                    '${s.global.round()}',
                    style: const TextStyle(
                      fontSize: 36,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'CV ${s.cv.round()} • '
                  'Profil ${s.form.round()} • '
                  'Tests ${s.assessment.round()}',
            ),
          ],
        ),
      ),
    );
  }

  Widget _goalCard(DashboardDto d) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Objectif : ${d.targetRole}',
            style: const TextStyle(
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          LinearProgressIndicator(
            value: d.matchPercent / 100,
            minHeight: 10,
          ),
          const SizedBox(height: 4),
          Text('${d.matchPercent}% de correspondance'),
        ],
      ),
    ),
  );

  Widget _missingTile(MissingSkill m) => Card(
    child: ListTile(
      title: Text(m.skillName),
      subtitle: LinearProgressIndicator(
        value: (
            m.currentLevel /
                (m.requiredLevel == 0 ? 1 : m.requiredLevel)
        ).clamp(0.0, 1.0),
      ),
      trailing: Text(
        '${m.currentLevel}/${m.requiredLevel}',
      ),
    ),
  );
}