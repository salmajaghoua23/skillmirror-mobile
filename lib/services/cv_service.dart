import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class PickedCv {
  final String storagePath;
  final String fileName;
  PickedCv(this.storagePath, this.fileName);
}

class CvService {
  final _db = Supabase.instance.client;

  /// Choisit un PDF/DOCX et l'envoie dans le bucket privé.
  Future<PickedCv?> pickAndUpload() async {
    final picked = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf', 'docx'],
    );
    if (picked == null) return null;
    final f = picked.files.single;
    if (f.path == null) return null;

    final uid = FirebaseAuth.instance.currentUser!.uid;
    final storagePath =
        '$uid/${DateTime.now().millisecondsSinceEpoch}.${f.extension}';
    await _db.storage.from('candidate_cvs').upload(storagePath, File(f.path!));
    return PickedCv(storagePath, f.name);
  }

  /// Enregistre le CV dans la table candidate_cvs.
  Future<void> register(String profileId, PickedCv cv) =>
      _db.from('candidate_cvs').insert({
        'profile_id': profileId,
        'file_name': cv.fileName,
        'storage_path': cv.storagePath,
        'is_main': true,
      });
}
