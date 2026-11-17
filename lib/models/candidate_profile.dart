class CandidateProfile {
  String fullName;
  String? email;
  int? age;
  String? gender; // M, F, NOT_SPECIFIED
  String? country, region, studyField, educationLevel, diploma;
  String? sector, availability, interviewLanguage;
  bool mobility;
  List<String> interests;
  String? targetOfferDescription, trainingObjective, cvStoragePath;

  CandidateProfile({
    this.fullName = '',
    this.email,
    this.age,
    this.gender,
    this.country,
    this.region,
    this.studyField,
    this.educationLevel,
    this.diploma,
    this.sector,
    this.availability,
    this.interviewLanguage,
    this.mobility = false,
    List<String>? interests,
    this.targetOfferDescription,
    this.trainingObjective,
    this.cvStoragePath,
  }) : interests = interests ?? [];

  factory CandidateProfile.fromJson(Map<String, dynamic> j) => CandidateProfile(
    fullName: j['fullName'] ?? '',
    email: j['email'],
    age: j['age'],
    gender: j['gender'],
    country: j['country'],
    region: j['region'],
    studyField: j['studyField'],
    educationLevel: j['educationLevel'],
    diploma: j['diploma'],
    sector: j['sector'],
    availability: j['availability'],
    interviewLanguage: j['interviewLanguage'],
    mobility: j['mobility'] ?? false,
    interests: List<String>.from(j['interests'] ?? []),
    targetOfferDescription: j['targetOfferDescription'],
    trainingObjective: j['trainingObjective'],
    cvStoragePath: j['cvStoragePath'],
  );

  Map<String, dynamic> profileJson() => {
    'fullName': fullName,
    'age': age,
    'gender': gender,
    'country': country,
    'region': region,
    'studyField': studyField,
    'educationLevel': educationLevel,
    'diploma': diploma,
  };

  Map<String, dynamic> preferencesJson() => {
    'sector': sector,
    'availability': availability,
    'mobility': mobility,
    'interviewLanguage': interviewLanguage,
    'interests': interests,
    'targetOfferDescription': targetOfferDescription,
    'trainingObjective': trainingObjective,
  };
}
