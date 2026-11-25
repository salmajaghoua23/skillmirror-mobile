package com.skillmirror.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.common.progress.AnalysisProgressService;
import com.skillmirror.skills.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GapAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(GapAnalysisService.class);

    private final CandidateProfileRepository profileRepo;
    private final CandidateSkillLevelRepository levelRepo;
    private final JobRoleRepository roleRepo;
    private final JobRoleSkillRepository roleSkillRepo;
    private final SkillRepository skillRepo;
    private final JobAiAnalysisService jobAiAnalysisService;
    private final GoalSkillRequirementRepository requirementRepo;
    private final JdbcTemplate jdbc;
    private final CvContextBuilder cvContextBuilder;
    private final AnalysisProgressService progress;

    private record SkillRequirement(
            UUID skillId,
            String skillName,
            int requiredLevel,
            boolean required
    ) {}

    private record GoalRequirements(
            String jobTitle,
            List<SkillRequirement> skills
    ) {}

    private record CvRow(String rawText, String data) {}

    // --------------------------------------------------
    // 1. Exigences du métier visé
    // --------------------------------------------------

    private GoalRequirements resolveRequirements(UUID profileId) {
        return resolveRequirements(profileId, false);
    }

    private GoalRequirements resolveRequirements(UUID profileId, boolean forceRefresh) {

        CandidateProfile profile = profileRepo
                .findByProfileId(profileId)
                .orElse(null);

        if (profile == null) {
            return new GoalRequirements("Objectif non défini", List.of());
        }

        if (!forceRefresh) {
            if (requirementRepo.existsByProfileId(profileId)) {
                return loadSavedRequirements(profileId);
            }
            // Jamais d'appel Ollama dans une requête HTTP :
            // l'analyse se fait en arrière-plan (GoalAnalysisJob)
            return new GoalRequirements("Analyse en cours", List.of());
        }

        // ---------- analyse forcée (appelée par GoalAnalysisJob) ----------
        String objective = nz(profile.getTrainingObjective());
        String offer = nz(profile.getTargetOfferDescription());

        progress.update(10, "Lecture du CV");
        CvRow cv = loadCvRow(profileId);
        boolean hasCv = !cv.rawText().isBlank() || !cv.data().equals("{}");
        String cvContext = hasCv ? cvContextBuilder.build(cv.rawText(), cv.data()) : "";
        List<String> cvSkills = loadCvTechnicalSkills(profileId, cv);

        if (cvContext.isBlank() && objective.isBlank()
                && offer.isBlank() && cvSkills.isEmpty()) {
            return new GoalRequirements("Objectif non défini", List.of());
        }

        try {
            List<Skill> catalog = new ArrayList<>(skillRepo.findAll());

            progress.update(30, "L'IA analyse le métier visé…");
            JsonNode result = jobAiAnalysisService.analyze(
                    cvContext, objective, offer, catalog, cvSkills);

            progress.update(80, "Enregistrement des compétences requises…");
            GoalRequirements requirements = convertAiResult(result, catalog);

            if (!requirements.skills().isEmpty()) {
                saveRequirements(profileId, requirements);
                log.info("Nouvelle analyse sauvegardée : {} | {} compétences",
                        requirements.jobTitle(), requirements.skills().size());
                return requirements;
            }
            log.warn("Ollama n'a retourné aucune compétence exploitable.");

        } catch (Exception e) {
            log.error("Erreur analyse Ollama : {}", e.getMessage(), e);
            progress.update(80, "IA indisponible, utilisation du référentiel…");
        }

        GoalRequirements fallback = resolveWithDatabase(profile, cvContext);
        if (!fallback.skills().isEmpty()) {
            saveRequirements(profileId, fallback);
        }
        return fallback;
    }

    private GoalRequirements loadSavedRequirements(UUID profileId) {

        List<GoalSkillRequirement> saved =
                requirementRepo.findByProfileId(profileId);

        List<SkillRequirement> requirements = saved.stream()
                .map(r -> {
                    Skill skill = skillRepo.findById(r.getSkillId()).orElse(null);
                    if (skill == null) {
                        return null;
                    }
                    return new SkillRequirement(
                            skill.getId(),
                            skill.getName(),
                            r.getRequiredLevel(),
                            Boolean.TRUE.equals(r.getRequired())
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        String jobTitle = saved.stream()
                .map(GoalSkillRequirement::getJobTitle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Objectif non défini");

        return new GoalRequirements(jobTitle, requirements);
    }

    private void saveRequirements(UUID profileId, GoalRequirements goal) {

        requirementRepo.deleteByProfileId(profileId);

        List<GoalSkillRequirement> entities = new ArrayList<>();

        for (SkillRequirement requirement : goal.skills()) {
            GoalSkillRequirement entity = new GoalSkillRequirement();
            entity.setProfileId(profileId);
            entity.setSkillId(requirement.skillId());
            entity.setJobTitle(goal.jobTitle());
            entity.setRequiredLevel(requirement.requiredLevel());
            entity.setRequired(requirement.required());
            entities.add(entity);
        }

        requirementRepo.saveAll(entities);
    }

    // --------------------------------------------------
    // 2. Lecture du CV du candidat
    // --------------------------------------------------

    /** Dernière analyse de CV du candidat (confirmée de préférence). */
    private CvRow loadCvRow(UUID profileId) {
        try {
            List<CvRow> rows = jdbc.query("""
                    select a.raw_text, a.data
                    from cv_analyses a
                    join candidate_cvs c on c.id = a.cv_id
                    where c.profile_id = ?
                      and a.status in ('CONFIRMED', 'EXTRACTED')
                    order by (a.status = 'CONFIRMED') desc, a.created_at desc
                    limit 1
                    """,
                    (rs, i) -> new CvRow(
                            nz(rs.getString("raw_text")),
                            rs.getString("data") == null ? "{}" : rs.getString("data")),
                    profileId);
            return rows.isEmpty() ? new CvRow("", "{}") : rows.get(0);
        } catch (Exception e) {
            log.warn("Lecture du CV impossible (vérifie les noms de colonnes) : {}",
                    e.getMessage());
            return new CvRow("", "{}");
        }
    }

    private List<String> loadCvTechnicalSkills(UUID profileId, CvRow cv) {

        Set<String> names = new LinkedHashSet<>(
                cvContextBuilder.technicalSkills(cv.data()));

        List<UUID> skillIds = levelRepo.findByProfileId(profileId).stream()
                .map(CandidateSkillLevel::getSkillId)
                .toList();

        skillRepo.findAllById(skillIds).stream()
                .filter(s -> !"SOFT".equalsIgnoreCase(s.getSkillType()))
                .map(Skill::getName)
                .filter(Objects::nonNull)
                .forEach(names::add);

        return new ArrayList<>(names);
    }

    public boolean hasRequirements(UUID profileId) {
        return requirementRepo.existsByProfileId(profileId);
    }

    // --------------------------------------------------
    // 3. Résoudre les compétences retournées par Ollama
    // --------------------------------------------------

    private GoalRequirements convertAiResult(JsonNode result, List<Skill> catalog) {

        String jobTitle = result.path("jobTitle").asText("");

        if (jobTitle.isBlank() || !result.path("skills").isArray()) {
            return new GoalRequirements("", List.of());
        }

        Map<UUID, Skill> skillsById = catalog.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Skill::getId, s -> s, (a, b) -> a));

        Map<String, Skill> skillsByName = catalog.stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(
                        s -> normalize(s.getName()), s -> s, (a, b) -> a));

        Map<UUID, SkillRequirement> requirements = new LinkedHashMap<>();

        for (JsonNode item : result.path("skills")) {

            String name = item.path("name").asText("").trim();
            String existingId = item.path("existingSkillId").asText("").trim();
            String category = item.path("category").asText("").trim();
            String skillType = item.path("skillType").asText("TECHNICAL").trim();
            int requiredLevel = item.path("requiredLevel").asInt(70);
            boolean required = item.path("required").asBoolean(true);

            // Compétences techniques uniquement
            if ("SOFT".equalsIgnoreCase(skillType)) {
                continue;
            }

            if (name.isBlank() || requiredLevel < 1 || requiredLevel > 100) {
                continue;
            }

            Skill skill = null;

            // A. ID proposé par Ollama
            if (!existingId.isBlank()) {
                try {
                    skill = skillsById.get(UUID.fromString(existingId));
                } catch (IllegalArgumentException e) {
                    log.warn("ID de compétence invalide retourné par Ollama : {}", existingId);
                }
            }

            // B. Nom exact normalisé
            if (skill == null) {
                skill = skillsByName.get(normalize(name));
            }

            // C. Création si aucune correspondance
            if (skill == null) {

                skill = createSkill(name, category, skillType, catalog);
                skill = skillRepo.saveAndFlush(skill);

                catalog.add(skill);
                skillsById.put(skill.getId(), skill);
                skillsByName.put(normalize(skill.getName()), skill);

                log.info("Nouvelle compétence créée : {} | catégorie : {}",
                        skill.getName(), skill.getCategory());

            } else {
                log.info("Compétence existante reconnue : {} | ID : {}",
                        skill.getName(), skill.getId());
            }

            requirements.putIfAbsent(
                    skill.getId(),
                    new SkillRequirement(
                            skill.getId(), skill.getName(), requiredLevel, required));
        }

        return new GoalRequirements(jobTitle, new ArrayList<>(requirements.values()));
    }

    // --------------------------------------------------
    // 4. Création dynamique d'une compétence
    // --------------------------------------------------

    private Skill createSkill(
            String name,
            String category,
            String skillType,
            List<Skill> catalog) {

        Skill skill = new Skill();

        skill.setId(UUID.randomUUID());
        skill.setName(name);
        skill.setCategory(category.isBlank() ? "Autres" : category);
        skill.setSkillType("SOFT".equalsIgnoreCase(skillType) ? "SOFT" : "TECHNICAL");
        skill.setDescription(
                "Compétence ajoutée automatiquement "
                        + "suite à l'analyse des exigences professionnelles.");

        UUID domainId = catalog.stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> s.getCategory().equalsIgnoreCase(skill.getCategory()))
                .map(Skill::getDomainId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        skill.setDomainId(domainId);

        return skill;
    }

    // --------------------------------------------------
    // 5. Normalisation
    // --------------------------------------------------

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);

        return normalized
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    // --------------------------------------------------
    // 6. Repli : ancien système job_roles
    // --------------------------------------------------

    private GoalRequirements resolveWithDatabase(
            CandidateProfile profile,
            String cvContext) {

        String goal = normalize(
                cvContext + " "
                        + nz(profile.getTrainingObjective()) + " "
                        + nz(profile.getTargetOfferDescription()));

        Optional<JobRole> role = roleRepo.findAll()
                .stream()
                .filter(r -> r.getName() != null
                        && !normalize(r.getName()).isEmpty()
                        && goal.contains(normalize(r.getName())))
                .max(Comparator.comparingInt(r -> r.getName().length()));

        if (role.isEmpty()) {
            return new GoalRequirements("Objectif non défini", List.of());
        }

        List<JobRoleSkill> roleSkills =
                roleSkillRepo.findByRoleId(role.get().getId());

        Map<UUID, String> names = skillRepo
                .findAllById(roleSkills.stream().map(JobRoleSkill::getSkillId).toList())
                .stream()
                .collect(Collectors.toMap(
                        Skill::getId,
                        s -> s.getName() == null ? "?" : s.getName()));

        List<SkillRequirement> requirements = new ArrayList<>();

        for (JobRoleSkill rs : roleSkills) {

            String name = names.get(rs.getSkillId());

            if (name == null) {
                continue;
            }

            requirements.add(new SkillRequirement(
                    rs.getSkillId(),
                    name,
                    Boolean.FALSE.equals(rs.getRequired()) ? 50 : 70,
                    !Boolean.FALSE.equals(rs.getRequired())));
        }

        return new GoalRequirements(role.get().getName(), requirements);
    }

    // --------------------------------------------------
    // 7. Compétences du candidat
    // --------------------------------------------------

    private Map<UUID, Integer> myLevels(UUID profileId) {

        return levelRepo.findByProfileId(profileId)
                .stream()
                .collect(Collectors.toMap(
                        CandidateSkillLevel::getSkillId,
                        l -> l.getLevelScore() == null ? 0 : l.getLevelScore(),
                        (a, b) -> a));
    }

    /** Appelée par GoalAnalysisJob. Pas de @Transactional : Ollama peut durer des minutes. */
    public void refreshGoalAnalysis(UUID profileId) {
        log.info("Actualisation forcée de l'objectif pour {}", profileId);
        resolveRequirements(profileId, true);
    }

    // --------------------------------------------------
    // 8. Compétences manquantes
    // --------------------------------------------------

    @Transactional
    public List<ScoringDtos.MissingSkillDto> findMissingSkills(UUID profileId) {

        GoalRequirements goal = resolveRequirements(profileId);

        return findMissingSkills(goal, myLevels(profileId));
    }

    private List<ScoringDtos.MissingSkillDto> findMissingSkills(
            GoalRequirements goal,
            Map<UUID, Integer> mine) {

        List<ScoringDtos.MissingSkillDto> missing = new ArrayList<>();

        for (SkillRequirement requirement : goal.skills()) {

            int current = mine.getOrDefault(requirement.skillId(), 0);

            if (current < requirement.requiredLevel()) {
                missing.add(new ScoringDtos.MissingSkillDto(
                        requirement.skillId(),
                        requirement.skillName(),
                        requirement.requiredLevel(),
                        current,
                        current == 0 ? "ABSENTE" : "INSUFFISANTE"));
            }
        }

        missing.sort(Comparator.comparingInt(
                (ScoringDtos.MissingSkillDto m) ->
                        m.requiredLevel() - m.currentLevel()).reversed());

        return missing;
    }

    // --------------------------------------------------
    // 9. Score de correspondance
    // --------------------------------------------------

    @Transactional
    public ScoringDtos.GoalComparisonDto compareWithGoal(UUID profileId) {

        GoalRequirements goal = resolveRequirements(profileId);

        if (goal.skills().isEmpty()) {
            return new ScoringDtos.GoalComparisonDto(
                    goal.jobTitle(), 0, List.of(), List.of());
        }

        Map<UUID, Integer> mine = myLevels(profileId);

        double total = 0;

        List<ScoringDtos.SkillLevelDto> strengths = new ArrayList<>();

        for (SkillRequirement requirement : goal.skills()) {

            int required = requirement.requiredLevel();
            int level = mine.getOrDefault(requirement.skillId(), 0);

            total += Math.min(level / (double) required, 1.0);

            if (level >= required) {
                strengths.add(new ScoringDtos.SkillLevelDto(
                        requirement.skillId(),
                        requirement.skillName(),
                        level,
                        ScoreService.label(level),
                        required));
            }
        }

        double percentage = total * 100.0 / goal.skills().size();

        return new ScoringDtos.GoalComparisonDto(
                goal.jobTitle(),
                Math.round(percentage * 10) / 10.0,
                strengths,
                findMissingSkills(goal, mine));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}