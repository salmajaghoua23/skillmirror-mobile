class RefItem {
  final String id;
  final String name;
  RefItem(this.id, this.name);
  factory RefItem.fromJson(Map<String, dynamic> j) =>
      RefItem(j['id'] as String, j['name'] as String);
}

class RecruiterProfile {
  final String fullName, email, position, companyName, companySize;
  final String country, city, companyDescription;
  final String? companyWebsite, hiringNeeds;
  final RefItem? sector;
  final List<RefItem> domains;

  RecruiterProfile({
    required this.fullName, required this.email, required this.position,
    required this.companyName, required this.companySize, required this.country,
    required this.city, required this.companyDescription,
    this.companyWebsite, this.hiringNeeds, this.sector, this.domains = const [],
  });

  factory RecruiterProfile.fromJson(Map<String, dynamic> j) => RecruiterProfile(
    fullName: j['fullName'] ?? '',
    email: j['email'] ?? '',
    position: j['position'] ?? '',
    companyName: j['companyName'] ?? '',
    companySize: j['companySize'] ?? '',
    country: j['country'] ?? '',
    city: j['city'] ?? '',
    companyDescription: j['companyDescription'] ?? '',
    companyWebsite: j['companyWebsite'],
    hiringNeeds: j['hiringNeeds'],
    sector: j['sector'] == null ? null : RefItem.fromJson(j['sector']),
    domains: (j['domains'] as List? ?? [])
        .map((e) => RefItem.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}