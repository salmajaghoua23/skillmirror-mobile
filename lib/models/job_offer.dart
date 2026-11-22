List<String> _strings(dynamic v) =>
    (v as List? ?? const []).map((e) => e.toString()).toList();

/// Libellés affichés dans l'application.
class OfferLabels {
  OfferLabels._();

  static const contractTypes = {
    'CDI': 'CDI',
    'CDD': 'CDD',
    'STAGE': 'Stage',
    'ALTERNANCE': 'Alternance',
    'FREELANCE': 'Freelance',
  };

  static const experienceLevels = {
    'JUNIOR': 'Junior',
    'INTERMEDIATE': 'Intermédiaire',
    'SENIOR': 'Senior',
  };

  static const offerStatuses = {
    'DRAFT': 'Brouillon',
    'PUBLISHED': 'Publiée',
    'CLOSED': 'Clôturée',
  };

  static const applicationStatuses = {
    'SENT': 'Reçue',
    'VIEWED': 'Vue',
    'SHORTLISTED': 'Présélectionnée',
    'ACCEPTED': 'Acceptée',
    'REJECTED': 'Refusée',
  };
}

String shortDate(String? iso) =>
    (iso == null || iso.length < 10) ? '' : iso.substring(0, 10);

class OfferSkill {
  final String name;
  final bool required;
  OfferSkill(this.name, {this.required = true});

  factory OfferSkill.fromJson(Map<String, dynamic> j) =>
      OfferSkill(j['name'] as String, required: j['required'] != false);

  Map<String, dynamic> toJson() => {'name': name, 'required': required};
}

class JobOffer {
  final String id, title, description, contractType, status;
  final String? location, experienceLevel, salaryRange, companyName, createdAt;
  final bool remote;
  final List<OfferSkill> skills;
  final int applicationsCount;

  JobOffer({
    required this.id,
    required this.title,
    required this.description,
    required this.contractType,
    required this.status,
    this.location,
    this.experienceLevel,
    this.salaryRange,
    this.companyName,
    this.createdAt,
    this.remote = false,
    this.skills = const [],
    this.applicationsCount = 0,
  });

  factory JobOffer.fromJson(Map<String, dynamic> j) => JobOffer(
    id: j['id'] as String,
    title: j['title'] ?? '',
    description: j['description'] ?? '',
    contractType: j['contractType'] ?? '',
    status: j['status'] ?? 'DRAFT',
    location: j['location'],
    experienceLevel: j['experienceLevel'],
    salaryRange: j['salaryRange'],
    companyName: j['companyName'],
    createdAt: j['createdAt'],
    remote: j['remote'] == true,
    skills: (j['skills'] as List? ?? const [])
        .map((e) => OfferSkill.fromJson(e as Map<String, dynamic>))
        .toList(),
    applicationsCount: (j['applicationsCount'] as num? ?? 0).toInt(),
  );
}

class JobApplication {
  final String id, offerId, candidateId, candidateName, status;
  final String? offerTitle, message, createdAt;
  final int? matchScore;

  JobApplication({
    required this.id,
    required this.offerId,
    required this.candidateId,
    required this.candidateName,
    required this.status,
    this.offerTitle,
    this.message,
    this.createdAt,
    this.matchScore,
  });

  factory JobApplication.fromJson(Map<String, dynamic> j) => JobApplication(
    id: j['id'] as String,
    offerId: j['offerId'] as String,
    candidateId: j['candidateId'] as String,
    candidateName: j['candidateName'] ?? 'Candidat',
    status: j['status'] ?? 'SENT',
    offerTitle: j['offerTitle'],
    message: j['message'],
    createdAt: j['createdAt'],
    matchScore: (j['matchScore'] as num?)?.toInt(),
  );
}

class CandidateSummary {
  final String profileId, fullName;
  final String? sector, country, region, educationLevel, availability;
  final int matchScore;
  final List<String> matchedSkills, missingSkills;

  CandidateSummary({
    required this.profileId,
    required this.fullName,
    required this.matchScore,
    this.sector,
    this.country,
    this.region,
    this.educationLevel,
    this.availability,
    this.matchedSkills = const [],
    this.missingSkills = const [],
  });

  factory CandidateSummary.fromJson(Map<String, dynamic> j) => CandidateSummary(
    profileId: j['profileId'] as String,
    fullName: j['fullName'] ?? 'Candidat',
    matchScore: (j['matchScore'] as num? ?? 0).toInt(),
    sector: j['sector'],
    country: j['country'],
    region: j['region'],
    educationLevel: j['educationLevel'],
    availability: j['availability'],
    matchedSkills: _strings(j['matchedSkills']),
    missingSkills: _strings(j['missingSkills']),
  );
}

class CandidateDetail {
  final String profileId, fullName;
  final String? email, country, region, studyField, educationLevel;
  final String? diploma, sector, availability, summary;
  final bool? mobility;
  final List<String> technicalSkills, softSkills;
  final List<Map<String, dynamic>> education, experiences, languages;
  final List<Map<String, dynamic>> certifications;

  CandidateDetail({
    required this.profileId,
    required this.fullName,
    this.email,
    this.country,
    this.region,
    this.studyField,
    this.educationLevel,
    this.diploma,
    this.sector,
    this.availability,
    this.summary,
    this.mobility,
    this.technicalSkills = const [],
    this.softSkills = const [],
    this.education = const [],
    this.experiences = const [],
    this.languages = const [],
    this.certifications = const [],
  });

  static List<Map<String, dynamic>> _maps(dynamic v) => (v as List? ?? const [])
      .map((e) => Map<String, dynamic>.from(e as Map))
      .toList();

  factory CandidateDetail.fromJson(Map<String, dynamic> j) => CandidateDetail(
    profileId: j['profileId'] as String,
    fullName: j['fullName'] ?? 'Candidat',
    email: j['email'],
    country: j['country'],
    region: j['region'],
    studyField: j['studyField'],
    educationLevel: j['educationLevel'],
    diploma: j['diploma'],
    sector: j['sector'],
    availability: j['availability'],
    summary: j['summary'],
    mobility: j['mobility'] as bool?,
    technicalSkills: _strings(j['technicalSkills']),
    softSkills: _strings(j['softSkills']),
    education: _maps(j['education']),
    experiences: _maps(j['experiences']),
    languages: _maps(j['languages']),
    certifications: _maps(j['certifications']),
  );
}

class RecruiterDashboard {
  final String fullName, companyName;
  final int totalOffers, publishedOffers, draftOffers, closedOffers;
  final int totalApplications, newApplications, shortlisted, accepted, rejected;
  final List<JobApplication> recentApplications;

  RecruiterDashboard({
    required this.fullName,
    required this.companyName,
    required this.totalOffers,
    required this.publishedOffers,
    required this.draftOffers,
    required this.closedOffers,
    required this.totalApplications,
    required this.newApplications,
    required this.shortlisted,
    required this.accepted,
    required this.rejected,
    required this.recentApplications,
  });

  static int _i(dynamic v) => (v as num? ?? 0).toInt();

  factory RecruiterDashboard.fromJson(Map<String, dynamic> j) =>
      RecruiterDashboard(
        fullName: j['fullName'] ?? '',
        companyName: j['companyName'] ?? '',
        totalOffers: _i(j['totalOffers']),
        publishedOffers: _i(j['publishedOffers']),
        draftOffers: _i(j['draftOffers']),
        closedOffers: _i(j['closedOffers']),
        totalApplications: _i(j['totalApplications']),
        newApplications: _i(j['newApplications']),
        shortlisted: _i(j['shortlisted']),
        accepted: _i(j['accepted']),
        rejected: _i(j['rejected']),
        recentApplications: (j['recentApplications'] as List? ?? const [])
            .map((e) => JobApplication.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
