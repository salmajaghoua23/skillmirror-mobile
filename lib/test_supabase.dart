import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class ConnectionTestPage extends StatefulWidget {
  const ConnectionTestPage({super.key});

  @override
  State<ConnectionTestPage> createState() => _ConnectionTestPageState();
}

class _ConnectionTestPageState extends State<ConnectionTestPage> {
  final emailController = TextEditingController();
  final passwordController = TextEditingController();

  String result = 'En attente du test...';
  bool loading = false;

  void showResult(String message) {
    if (!mounted) return;
    setState(() {
      result = message;
      loading = false;
    });
  }

  Future<void> register() async {
    setState(() => loading = true);

    try {
      final credential = await FirebaseAuth.instance
          .createUserWithEmailAndPassword(
            email: emailController.text.trim(),
            password: passwordController.text.trim(),
          );

      final user = credential.user!;

      showResult(
        'INSCRIPTION REUSSIE\n'
        'UID: ${user.uid}\n'
        'EMAIL: ${user.email}',
      );
    } on FirebaseAuthException catch (e) {
      showResult('ERREUR FIREBASE: ${e.code}\n${e.message}');
    } catch (e) {
      showResult('ERREUR: $e');
    }
  }

  Future<void> signIn() async {
    setState(() => loading = true);

    try {
      final credential = await FirebaseAuth.instance.signInWithEmailAndPassword(
        email: emailController.text.trim(),
        password: passwordController.text.trim(),
      );

      showResult(
        'CONNEXION REUSSIE\n'
        'UID: ${credential.user!.uid}',
      );
    } on FirebaseAuthException catch (e) {
      showResult('ERREUR FIREBASE: ${e.code}\n${e.message}');
    } catch (e) {
      showResult('ERREUR: $e');
    }
  }

  Future<void> testToken() async {
    setState(() => loading = true);

    try {
      final user = FirebaseAuth.instance.currentUser;

      if (user == null) {
        showResult('Connecte-toi d abord.');
        return;
      }

      // Forcer le renouvellement du JWT Firebase
      await user.getIdToken(true);

      // Récupérer les nouvelles claims
      final tokenResult = await user.getIdTokenResult();
      final claims = tokenResult.claims ?? {};

      final role = claims['role'];

      showResult(
        'JWT FIREBASE VALIDE\n'
        'UID: ${user.uid}\n'
        'ROLE: $role\n'
        'ISSUER: ${claims['iss']}\n'
        'AUDIENCE: ${claims['aud']}\n'
        'TOKEN PRESENT: ${tokenResult.token != null}',
      );
    } catch (e) {
      showResult('ERREUR JWT: $e');
    }
  }

  Future<void> testSupabase() async {
    setState(() => loading = true);

    try {
      final user = FirebaseAuth.instance.currentUser;

      if (user == null) {
        showResult('Aucun utilisateur Firebase connecté.');
        return;
      }

      final data = await Supabase.instance.client
          .from('profiles_test')
          .select('user_id, display_name');

      showResult(
        'LECTURE SUPABASE REUSSIE\n'
        'UTILISATEUR: ${user.uid}\n'
        'PROFILS ACCESSIBLES:\n'
        '$data',
      );
    } catch (e) {
      showResult('ERREUR SUPABASE:\n$e');
    }
  }

  Future<void> signOut() async {
    await FirebaseAuth.instance.signOut();
    showResult('DECONNEXION REUSSIE');
  }

  @override
  void dispose() {
    emailController.dispose();
    passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('SkillMirror - Connection Tests')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'TEST FIREBASE + SUPABASE',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 20),

            TextField(
              controller: emailController,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(
                labelText: 'Email',
                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 12),

            TextField(
              controller: passwordController,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'Mot de passe',
                border: OutlineInputBorder(),
              ),
            ),

            const SizedBox(height: 20),

            ElevatedButton(
              onPressed: loading ? null : register,
              child: const Text('1. Inscription Firebase'),
            ),

            ElevatedButton(
              onPressed: loading ? null : signIn,
              child: const Text('2. Connexion Firebase'),
            ),

            ElevatedButton(
              onPressed: loading ? null : testToken,
              child: const Text('3. Tester JWT Firebase'),
            ),

            ElevatedButton(
              onPressed: loading ? null : testSupabase,
              child: const Text('4. Lire les profils Supabase'),
            ),

            OutlinedButton(
              onPressed: loading ? null : signOut,
              child: const Text('Déconnexion'),
            ),

            const SizedBox(height: 25),

            const Text(
              'RESULTAT DU TEST',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),

            const SizedBox(height: 10),

            if (loading) const Center(child: CircularProgressIndicator()),

            Container(
              padding: const EdgeInsets.all(15),
              decoration: BoxDecoration(
                color: Colors.black12,
                borderRadius: BorderRadius.circular(10),
              ),
              child: SelectableText(result),
            ),
          ],
        ),
      ),
    );
  }
}
