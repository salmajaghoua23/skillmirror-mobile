
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/score_models.dart';
import '../../providers/score_provider.dart';

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

  bool _refreshingAnalysis = false;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback(
          (_) => context.read<ScoreProvider>().load(widget.profileId),
    );
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
          'Une nouvelle analyse de votre objectif professionnel '
              'sera effectuée avec l’IA. Les anciennes recommandations '
              'seront remplacées par les nouvelles.',
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

    setState(() => _refreshingAnalysis = true);

    try {
      await context
          .read<ScoreProvider>()
          .refreshAnalysis(widget.profileId);

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Analyse et recommandations actualisées.'),
          backgroundColor: Colors.green,
        ),
      );

    } catch (e) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Erreur lors de l’analyse : $e'),
          backgroundColor: Colors.red,
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _refreshingAnalysis = false);
      }
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
            onPressed: _refreshingAnalysis || p.loading
                ? null
                : _refreshAnalysis,
            icon: _refreshingAnalysis
                ? const SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(
                strokeWidth: 2,
              ),
            )
                : const Icon(Icons.refresh),
          ),
          const SizedBox(width: 8),
        ],
      ),

      body: p.loading || _refreshingAnalysis
          ? const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 12),
            Text('Analyse en cours...'),
          ],
        ),
      )
          : p.error != null
          ? Center(child: Text(p.error!))
          : p.data == null
          ? const SizedBox()
          : RefreshIndicator(
        onRefresh: () =>
            p.load(widget.profileId),
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
