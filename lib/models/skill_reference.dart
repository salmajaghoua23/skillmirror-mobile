class SkillItem {
  final String id, name, type;
  SkillItem({required this.id, required this.name, required this.type});

  factory SkillItem.fromJson(Map<String, dynamic> j) => SkillItem(
    id: j['id'] as String,
    name: j['name'] ?? '',
    type: j['type'] ?? 'TECHNICAL',
  );
}

class SkillDomain {
  final String id, name;
  final String? description;
  final List<SkillItem> skills;
  SkillDomain({
    required this.id,
    required this.name,
    this.description,
    this.skills = const [],
  });

  factory SkillDomain.fromJson(Map<String, dynamic> j) => SkillDomain(
    id: j['id'] as String,
    name: j['name'] ?? '',
    description: j['description'],
    skills: (j['skills'] as List? ?? const [])
        .map((e) => SkillItem.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

class JobRoleItem {
  final String id, name;
  final String? description;
  JobRoleItem({required this.id, required this.name, this.description});

  factory JobRoleItem.fromJson(Map<String, dynamic> j) => JobRoleItem(
    id: j['id'] as String,
    name: j['name'] ?? '',
    description: j['description'],
  );
}

class RoleSkill {
  final String name, type;
  final bool required;
  RoleSkill({required this.name, required this.type, required this.required});

  factory RoleSkill.fromJson(Map<String, dynamic> j) => RoleSkill(
    name: j['name'] ?? '',
    type: j['type'] ?? 'TECHNICAL',
    required: j['required'] != false,
  );
}

class JobRoleDetail {
  final String id, name;
  final String? description;
  final List<RoleSkill> skills;
  JobRoleDetail({
    required this.id,
    required this.name,
    this.description,
    this.skills = const [],
  });

  factory JobRoleDetail.fromJson(Map<String, dynamic> j) => JobRoleDetail(
    id: j['id'] as String,
    name: j['name'] ?? '',
    description: j['description'],
    skills: (j['skills'] as List? ?? const [])
        .map((e) => RoleSkill.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}