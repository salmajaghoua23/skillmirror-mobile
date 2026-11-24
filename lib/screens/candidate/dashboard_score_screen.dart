import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/score_models.dart';
import '../../providers/score_provider.dart';

class DashboardScoreScreen extends StatefulWidget {
  final String profileId;
  const DashboardScoreScreen({super.key, required this.profileId});
  @override
  State<DashboardScoreScreen> createState() => _DashboardScoreScreenState();
}

class _DashboardScoreScreenState extends State<DashboardScoreScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback(
            (_) => context.read<ScoreProvider>().load(widget.profileId));
  }

  Color _prio(String p) =>
      p == 'HIGH' ? Colors.red : p == 'MEDIUM' ? Colors.orange : Colors.green;

  @override
  Widget build(BuildContext context) {
    final p = context.watch<ScoreProvider>();
    return Scaffold(
      appBar: AppBar(title: const Text('Mon score')),
      body: p.loading
          ? const Center(child: CircularProgressIndicator())
          : p.error != null
          ? Center(child: Text(p.error!))
          : p.data == null
          ? const SizedBox()
          : RefreshIndicator(
        onRefresh: () => p.load(widget.profileId),
        child: ListView(padding: const EdgeInsets.all(16), children: [
          _scoreGauge(p.data!),
          const SizedBox(height: 16),
          _goalCard(p.data!),
          const SizedBox(height: 16),
          const Text('Compétences à améliorer',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ...p.data!.missing.map(_missingTile),
          const SizedBox(height: 16),
          const Text('Recommandations',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ...p.data!.recommendations.map((r) => Card(
            child: ListTile(
              leading: Icon(Icons.lightbulb, color: _prio(r.priority)),
              title: Text(r.message),
              subtitle: Text('${r.type} • ${r.priority}'),
            ),
          )),
        ]),
      ),
    );
  }

  Widget _scoreGauge(DashboardDto d) {
    final s = d.score;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(children: [
          SizedBox(
            width: 140,
            height: 140,
            child: Stack(alignment: Alignment.center, children: [
              SizedBox.expand(
                  child: CircularProgressIndicator(value: s.global / 100, strokeWidth: 12)),
              Text('${s.global.round()}',
                  style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold)),
            ]),
          ),
          const SizedBox(height: 12),
          Text('CV ${s.cv.round()}  •  Profil ${s.form.round()}  •  Tests ${s.assessment.round()}'),
        ]),
      ),
    );
  }

  Widget _goalCard(DashboardDto d) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Objectif : ${d.targetRole}',
            style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        LinearProgressIndicator(value: d.matchPercent / 100, minHeight: 10),
        const SizedBox(height: 4),
        Text('${d.matchPercent}% de correspondance'),
      ]),
    ),
  );

  Widget _missingTile(MissingSkill m) => Card(
    child: ListTile(
      title: Text(m.skillName),
      subtitle: LinearProgressIndicator(
          value: (m.currentLevel / (m.requiredLevel == 0 ? 1 : m.requiredLevel)).clamp(0.0, 1.0)),
      trailing: Text('${m.currentLevel}/${m.requiredLevel}'),
    ),
  );
}