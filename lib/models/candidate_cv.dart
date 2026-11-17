class CandidateCv {
  final String id;
  final String fileName;
  final int sizeBytes;
  final bool active;
  final DateTime? uploadedAt;

  CandidateCv({
    required this.id,
    required this.fileName,
    required this.sizeBytes,
    required this.active,
    this.uploadedAt,
  });

  factory CandidateCv.fromJson(Map<String, dynamic> j) => CandidateCv(
    id: j['id'],
    fileName: j['fileName'] ?? 'cv',
    sizeBytes: (j['sizeBytes'] ?? 0) as int,
    active: j['active'] ?? false,
    uploadedAt: DateTime.tryParse(j['uploadedAt'] ?? ''),
  );
}
