import 'package:firebase_auth/firebase_auth.dart';
import 'package:supabase_flutter/supabase_flutter.dart' hide User;

class ProfileService {
  final _db = Supabase.instance.client;
  User get _user => FirebaseAuth.instance.currentUser!;

  /// Rôle choisi sur l'écran d'accueil, avant la connexion Google.
  static String? pendingRole;
  Future<void> _refreshToken() => _user.getIdToken(true);

  Future<Map<String, dynamic>?> getAccount() =>
      _db.from('profiles').select().eq('firebase_uid', _user.uid).maybeSingle();

  /// Retourne le compte ; le crée d'abord si un rôle est en attente.
  Future<Map<String, dynamic>?> getAccountOrCreate() async {
    await _refreshToken();
    var account = await getAccount();
    if (account == null && pendingRole != null) {
      await createAccount(pendingRole!);
      pendingRole = null;
      account = await getAccount();
    }
    return account;
  }

  Future<String> profileId() async => (await getAccount())!['id'] as String;

  /// role = 'candidate' ou 'recruiter'. Ne modifie pas un compte existant.
  Future<void> createAccount(String role) async {
    await _refreshToken();
    if (await getAccount() != null) return;
    await _db.from('profiles').insert({
      'firebase_uid': _user.uid,
      'full_name': _user.displayName ?? _user.email ?? '',
      'email': _user.email ?? '',
      'role': role,
    });
  }

  Future<void> saveCandidate(
    Map<String, dynamic> data, {
    required String fullName,
  }) async {
    final id = await profileId();
    await _db.from('candidate_profiles').upsert({
      ...data,
      'profile_id': id,
      'updated_at': DateTime.now().toIso8601String(),
    }, onConflict: 'profile_id');
    await _db
        .from('profiles')
        .update({'full_name': fullName, 'onboarding_done': true})
        .eq('id', id);
  }

  Future<void> saveRecruiter(
    Map<String, dynamic> data, {
    required String fullName,
  }) async {
    final id = await profileId();
    await _db.from('recruiter_profiles').upsert({
      ...data,
      'profile_id': id,
      'updated_at': DateTime.now().toIso8601String(),
    }, onConflict: 'profile_id');
    await _db
        .from('profiles')
        .update({'full_name': fullName, 'onboarding_done': true})
        .eq('id', id);
  }
}
