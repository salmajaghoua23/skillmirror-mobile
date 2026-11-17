import 'package:flutter/material.dart';

import '../../models/recruiter_profile.dart';
import '../../services/recruiter_service.dart';
import '../../widgets/recruiter_profile_form.dart';

class RecruiterEditScreen extends StatelessWidget {
  final RecruiterProfile profile;
  const RecruiterEditScreen({super.key, required this.profile});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Modifier mon profil')),
      body: RecruiterProfileForm(
        initial: profile,
        submitLabel: 'Enregistrer',
        onSubmit: (data) async {
          await RecruiterService().saveProfile(data);
          if (!context.mounted) return;
          Navigator.pop(context, true); // true = profil modifié
        },
      ),
    );
  }
}
