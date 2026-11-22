import 'package:flutter/material.dart';

/// Petite pastille colorée pour les statuts d'offre / de candidature.
class StatusChip extends StatelessWidget {
  final String status;
  final String label;
  const StatusChip({super.key, required this.status, required this.label});

  static Color colorFor(String status) {
    switch (status) {
      case 'PUBLISHED':
      case 'ACCEPTED':
        return Colors.green;
      case 'DRAFT':
      case 'SENT':
        return Colors.blueGrey;
      case 'VIEWED':
        return Colors.blue;
      case 'SHORTLISTED':
        return Colors.orange;
      case 'CLOSED':
      case 'REJECTED':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = colorFor(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: c.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: TextStyle(color: c, fontSize: 12, fontWeight: FontWeight.w700),
      ),
    );
  }
}
