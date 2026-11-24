
package com.skillmirror.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.skills.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // --------------------------------------------------
    // 1. Analyse dynamique de l'objectif professionnel
    // --------------------------------------------------


    private GoalRequirements resolveRequirements(UUID profileId) {
        return resolveRequirements(profileId, false);
    }

    private GoalRequirements resolveRequirements(
            UUID profileId,
            boolean forceRefresh
    ) {
        CandidateProfile profile = profileRepo
                .findByProfileId(profileId)
                .orElse(null);

        if (profile == null) {
            return new GoalRequirements("Objectif non défini", List.of());
        }

        // 1. Utiliser les données sauvegardées si elles existent
        if (!forceRefresh && requirementRepo.existsByProfileId(profileId)) {
            log.info("Chargement des exigences sauvegardées pour {}", profileId);
            return loadSavedRequirements(profileId);
        }

        String objective = nz(profile.getTrainingObjective());
        String offer = nz(profile.getTargetOfferDescription());

        if (objective.isBlank() && offer.isBlank()) {
            return new GoalRequirements("Objectif non défini", List.of());
        }

        // 2. Nouvelle analyse Ollama
        try {
            List<Skill> catalog = new ArrayList<>(skillRepo.findAll());

            JsonNode result = jobAiAnalysisService.analyze(
                    objective,
                    offer,
                    catalog
            );

            GoalRequirements requirements =
                    convertAiResult(result, catalog);

            if (!requirements.skills().isEmpty()) {

                // Remplacer les anciennes exigences par les nouvelles
                saveRequirements(profileId, requirements);

                log.info(
                        "Nouvelle analyse sauvegardée : {} | {} compétences",
                        requirements.jobTitle(),
                        requirements.skills().size()
                );

                return requirements;
            }

            log.warn("Ollama n'a retourné aucune compétence exploitable.");

        } catch (Exception e) {
            log.error("Erreur analyse Ollama : {}", e.getMessage(), e);
        }

        // 3. Fallback sur job_roles si Ollama échoue
        GoalRequirements fallback = resolveWithDatabase(profile);

        if (!fallback.skills().isEmpty()) {
            saveRequirements(profileId, fallback);
        }

        return fallback;
    }


    // Charger les exigences depuis PostgreSQL
    private GoalRequirements loadSavedRequirements(UUID profileId) {

        List<GoalSkillRequirement> saved =
                requirementRepo.findByProfileId(profileId);

        List<SkillRequirement> requirements = saved.stream()
                .map(r -> {
                    Skill skill = skillRepo.findById(r.getSkillId())
                            .orElse(null);

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


    // Remplacer les anciennes exigences par les nouvelles
    private void saveRequirements(
            UUID profileId,
            GoalRequirements goal
    ) {
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
    // 2. Résoudre les compétences retournées par Ollama
    // --------------------------------------------------

    private GoalRequirements convertAiResult(
            JsonNode result,
            List<Skill> catalog) {

        String jobTitle =
                result.path("jobTitle").asText("");

        if (jobTitle.isBlank()
                || !result.path("skills").isArray()) {
            return new GoalRequirements("", List.of());
        }

        Map<UUID, Skill> skillsById = catalog.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(
                        Skill::getId,
                        s -> s,
                        (a, b) -> a
                ));

        Map<String, Skill> skillsByName = catalog.stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(
                        s -> normalize(s.getName()),
                        s -> s,
                        (a, b) -> a
                ));

        Map<UUID, SkillRequirement> requirements =
                new LinkedHashMap<>();

        for (JsonNode item : result.path("skills")) {

            String name =
                    item.path("name").asText("").trim();

            String existingId =
                    item.path("existingSkillId").asText("").trim();

            String category =
                    item.path("category").asText("").trim();

            String skillType =
                    item.path("skillType").asText("TECHNICAL").trim();

            int requiredLevel =
                    item.path("requiredLevel").asInt(70);

            boolean required =
                    item.path("required").asBoolean(true);

            if (name.isBlank()
                    || requiredLevel < 1
                    || requiredLevel > 100) {
                continue;
            }

            Skill skill = null;

            // A. Vérifier l'ID proposé par Ollama
            if (!existingId.isBlank()) {
                try {
                    UUID skillId = UUID.fromString(existingId);
                    skill = skillsById.get(skillId);
                } catch (IllegalArgumentException e) {
                    log.warn(
                            "ID de compétence invalide retourné par Ollama : {}",
                            existingId
                    );
                }
            }

            // B. Si aucun ID valide, vérifier le nom exact normalisé
            if (skill == null) {
                skill = skillsByName.get(normalize(name));
            }

            // C. Si aucune correspondance, créer la compétence
            if (skill == null) {

                skill = createSkill(
                        name,
                        category,
                        skillType,
                        catalog
                );

                skill = skillRepo.saveAndFlush(skill);

                catalog.add(skill);

                skillsById.put(skill.getId(), skill);
                skillsByName.put(
                        normalize(skill.getName()),
                        skill
                );

                log.info(
                        "Nouvelle compétence créée : {} | catégorie : {}",
                        skill.getName(),
                        skill.getCategory()
                );

            } else {

                log.info(
                        "Compétence existante reconnue : {} | ID : {}",
                        skill.getName(),
                        skill.getId()
                );
            }

            requirements.putIfAbsent(
                    skill.getId(),
                    new SkillRequirement(
                            skill.getId(),
                            skill.getName(),
                            requiredLevel,
                            required
                    )
            );
        }

        return new GoalRequirements(
                jobTitle,
                new ArrayList<>(requirements.values())
        );
    }

    // --------------------------------------------------
    // 3. Création dynamique d'une compétence
    // --------------------------------------------------

    private Skill createSkill(
            String name,
            String category,
            String skillType,
            List<Skill> catalog) {

        Skill skill = new Skill();

        skill.setId(UUID.randomUUID());
        skill.setName(name);
        skill.setCategory(
                category.isBlank() ? "Autres" : category
        );

        skill.setSkillType(
                "SOFT".equalsIgnoreCase(skillType)
                        ? "SOFT"
                        : "TECHNICAL"
        );

        skill.setDescription(
                "Compétence ajoutée automatiquement "
                        + "suite à l'analyse des exigences professionnelles."
        );

        // Réutiliser le domaine d'une compétence de même catégorie,
        // si un domaine correspondant existe déjà dans le catalogue.
        UUID domainId = catalog.stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> s.getCategory().equalsIgnoreCase(
                        skill.getCategory()
                ))
                .map(Skill::getDomainId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        skill.setDomainId(domainId);

        return skill;
    }

    // --------------------------------------------------
    // 4. Normalisation simple pour les noms exacts
    // --------------------------------------------------

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    // --------------------------------------------------
    // 5. Fallback : ancien système job_roles
    // --------------------------------------------------

    private GoalRequirements resolveWithDatabase(
            CandidateProfile profile) {

        String goal = normalize(
                nz(profile.getTrainingObjective())
                        + " "
                        + nz(profile.getTargetOfferDescription())
        );

        Optional<JobRole> role = roleRepo.findAll()
                .stream()
                .filter(r ->
                        r.getName() != null
                                && !normalize(r.getName()).isEmpty()
                                && goal.contains(normalize(r.getName()))
                )
                .max(Comparator.comparingInt(
                        r -> r.getName().length()
                ));

        if (role.isEmpty()) {
            return new GoalRequirements(
                    "Objectif non défini", List.of()
            );
        }

        List<JobRoleSkill> roleSkills =
                roleSkillRepo.findByRoleId(role.get().getId());

        Map<UUID, String> names = skillRepo
                .findAllById(
                        roleSkills.stream()
                                .map(JobRoleSkill::getSkillId)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        Skill::getId,
                        s -> s.getName() == null ? "?" : s.getName()
                ));

        List<SkillRequirement> requirements =
                new ArrayList<>();

        for (JobRoleSkill rs : roleSkills) {

            String name = names.get(rs.getSkillId());

            if (name == null) {
                continue;
            }

            requirements.add(
                    new SkillRequirement(
                            rs.getSkillId(),
                            name,
                            Boolean.FALSE.equals(rs.getRequired())
                                    ? 50 : 70,
                            !Boolean.FALSE.equals(rs.getRequired())
                    )
            );
        }

        return new GoalRequirements(
                role.get().getName(),
                requirements
        );
    }

    // --------------------------------------------------
    // 6. Récupérer les compétences du candidat
    // --------------------------------------------------

    private Map<UUID, Integer> myLevels(UUID profileId) {

        return levelRepo.findByProfileId(profileId)
                .stream()
                .collect(Collectors.toMap(
                        CandidateSkillLevel::getSkillId,
                        l -> l.getLevelScore() == null
                                ? 0 : l.getLevelScore(),
                        (a, b) -> a
                ));
    }
    @Transactional
    public void refreshGoalAnalysis(UUID profileId) {

        log.info("Actualisation forcée de l'objectif pour {}", profileId);

        // true = ignorer le cache et rappeler Ollama
        resolveRequirements(profileId, true);
    }

    // --------------------------------------------------
    // 7. Trouver les compétences manquantes
    // --------------------------------------------------

    @Transactional
    public List<ScoringDtos.MissingSkillDto> findMissingSkills(
            UUID profileId) {

        GoalRequirements goal =
                resolveRequirements(profileId);

        return findMissingSkills(
                goal,
                myLevels(profileId)
        );
    }

    private List<ScoringDtos.MissingSkillDto> findMissingSkills(
            GoalRequirements goal,
            Map<UUID, Integer> mine) {

        List<ScoringDtos.MissingSkillDto> missing =
                new ArrayList<>();

        for (SkillRequirement requirement : goal.skills()) {

            int current = mine.getOrDefault(
                    requirement.skillId(), 0
            );

            if (current < requirement.requiredLevel()) {

                missing.add(
                        new ScoringDtos.MissingSkillDto(
                                requirement.skillId(),
                                requirement.skillName(),
                                requirement.requiredLevel(),
                                current,
                                current == 0
                                        ? "ABSENTE"
                                        : "INSUFFISANTE"
                        )
                );
            }
        }

        missing.sort(
                Comparator.comparingInt(
                        (ScoringDtos.MissingSkillDto m) ->
                                m.requiredLevel() - m.currentLevel()
                ).reversed()
        );

        return missing;
    }

    // --------------------------------------------------
    // 8. Calcul du score de correspondance
    // --------------------------------------------------

    @Transactional
    public ScoringDtos.GoalComparisonDto compareWithGoal(
            UUID profileId) {

        GoalRequirements goal =
                resolveRequirements(profileId);

        if (goal.skills().isEmpty()) {
            return new ScoringDtos.GoalComparisonDto(
                    goal.jobTitle(),
                    0,
                    List.of(),
                    List.of()
            );
        }

        Map<UUID, Integer> mine = myLevels(profileId);

        double total = 0;

        List<ScoringDtos.SkillLevelDto> strengths =
                new ArrayList<>();

        for (SkillRequirement requirement : goal.skills()) {

            int required = requirement.requiredLevel();

            int level = mine.getOrDefault(
                    requirement.skillId(), 0
            );

            total += Math.min(
                    level / (double) required,
                    1.0
            );

            if (level >= required) {
                strengths.add(
                        new ScoringDtos.SkillLevelDto(
                                requirement.skillId(),
                                requirement.skillName(),
                                level,
                                ScoreService.label(level),
                                required
                        )
                );
            }
        }

        double percentage =
                total * 100.0 / goal.skills().size();

        return new ScoringDtos.GoalComparisonDto(
                goal.jobTitle(),
                Math.round(percentage * 10) / 10.0,
                strengths,
                findMissingSkills(goal, mine)
        );
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
