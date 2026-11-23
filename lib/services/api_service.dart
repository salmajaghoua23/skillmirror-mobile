import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:firebase_auth/firebase_auth.dart';
import 'package:http/http.dart' as http;

import '../config/api_config.dart';

class ApiException implements Exception {
  final int? status;
  final String message;
  ApiException(this.message, {this.status});
  @override
  String toString() => message;
}

class ApiService {
  static const _timeout = Duration(seconds: 20);

  Future<Map<String, String>> _headers() async {
    final token = await FirebaseAuth.instance.currentUser?.getIdToken();
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  Future<dynamic> get(String path) => _send(
    () async => http.get(
      Uri.parse('${ApiConfig.baseUrl}$path'),
      headers: await _headers(),
    ),
  );

  Future<dynamic> put(String path, Object body) => _send(
    () async => http.put(
      Uri.parse('${ApiConfig.baseUrl}$path'),
      headers: await _headers(),
      body: jsonEncode(body),
    ),
  );
  Future<dynamic> post(String path, Object body, {Duration? timeout}) => _send(
        () async => http.post(
      Uri.parse('${ApiConfig.baseUrl}$path'),
      headers: await _headers(),
      body: jsonEncode(body),
    ),
    timeout: timeout,
  );

  Future<dynamic> delete(String path) => _send(
    () async => http.delete(
      Uri.parse('${ApiConfig.baseUrl}$path'),
      headers: await _headers(),
    ),
  );

  /// Envoi d'un fichier (multipart), champ "file"
  Future<dynamic> uploadFile(
    String path,
    String filePath,
    String fileName,
  ) => _send(() async {
    final req = http.MultipartRequest(
      'POST',
      Uri.parse('${ApiConfig.baseUrl}$path'),
    );
    final headers = await _headers();
    headers.remove('Content-Type'); // multipart fixe son propre Content-Type
    req.headers.addAll(headers);
    req.files.add(
      await http.MultipartFile.fromPath('file', filePath, filename: fileName),
    );
    return http.Response.fromStream(await req.send());
  });

  Future<dynamic> _send(
      Future<http.Response> Function() call, {
        Duration? timeout,
      }) async {
    try {
      return _handle(await call().timeout(timeout ?? _timeout));
    } on TimeoutException {
      throw ApiException(
        'Le serveur ne répond pas. Vérifiez qu\'il est démarré '
            'et que l\'adresse API_URL est correcte (${ApiConfig.baseUrl}).',
      );
    } on SocketException {
      throw ApiException('Serveur injoignable (${ApiConfig.baseUrl}).');
    }
  }

  dynamic _handle(http.Response r) {
    if (r.statusCode >= 200 && r.statusCode < 300) {
      return r.body.isEmpty ? null : jsonDecode(utf8.decode(r.bodyBytes));
    }
    throw ApiException(_messageFrom(r), status: r.statusCode);
  }

  String _messageFrom(http.Response r) {
    if (r.statusCode == 401) return 'Session expirée, reconnectez-vous.';
    try {
      final j = jsonDecode(utf8.decode(r.bodyBytes));
      if (j is Map) {
        final errors = j['errors'];
        if (errors is Map && errors.isNotEmpty) {
          return errors.values.join('\n');
        }
        if (j['message'] is String && (j['message'] as String).isNotEmpty) {
          return j['message'] as String;
        }
      }
    } catch (_) {}
    return 'Erreur serveur (${r.statusCode})';
  }
}
