package com.skillmirror.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillmirror.skills.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobAiAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(JobAiAnalysisService.class);

    @Value("${ollama.enabled}")
    private boolean enabled;

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.model}")
    private String model;

    @Value("${ollama.timeout-seconds}")
    private long timeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static final int MIN_SKILLS = 3;
    private static final int MAX_SKILLS = 12;

    private static final String SCHEMA = """
        {
          "type": "object",
          "properties": {
            "jobTitle": {"type": "string"},
            "skills": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "name": {"type": "string"},
                  "existingSkillId": {"type": "string"},
                  "category": {"type": "string"},
                  "skillType": {"type": "string", "enum": ["TECHNICAL"]},
                  "requiredLevel": {"type": "integer"},
                  "required": {"type": "boolean"}
                },
                "required": [
                  "name", "existingSkillId", "category",
                  "skillType", "requiredLevel", "required"
                ]
              }
            }
          },
          "required": ["jobTitle", "skills"]
        }
        """;

    /**
     * @param cvContext             profil, formations et expériences issus du CV (peut être vide)
     * @param trainingObjective     objectif déclaré (facultatif)
     * @param targetOfferDescription offre visée (facultatif)
     * @param catalog               catalogue complet des compétences
     * @param cvTechnicalSkills     compétences techniques déjà présentes dans le CV (peut être vide)
     */
    public JsonNode analyze(
            String cvContext,
            String trainingObjective,
            String targetOfferDescription,
            List<Skill> catalog,
            List<String> cvTechnicalSkills) {

        if (!enabled) {
            throw new IllegalStateException("L'analyse IA est désactivée.");
        }

        String context = nz(cvContext);
        String objective = nz(trainingObjective);
        String offer = nz(targetOfferDescription);
        List<String> cvSkills = cvTechnicalSkills == null
                ? List.of() : cvTechnicalSkills;

        // Seul cas bloquant : aucune information du tout.
        if (context.isBlank() && objective.isBlank()
                && offer.isBlank() && cvSkills.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucune information (CV, objectif ou offre) pour analyser le métier.");
        }

        try {
            return analyzeWithOllama(context, objective, offer, catalog, cvSkills);
        } catch (Exception e) {
            log.error("Erreur analyse métier Ollama : {}", e.getMessage(), e);
            throw new IllegalStateException(
                    "Impossible d'analyser l'objectif professionnel.", e);
        }
    }

    private JsonNode analyzeWithOllama(
            String cvContext,
            String objective,
            String offer,
            List<Skill> catalog,
            List<String> cvSkills) throws Exception {

        // Catalogue allégé : compétences techniques uniquement, id + nom
        List<Map<String, Object>> catalogData = catalog.stream()
                .filter(s -> s.getId() != null && s.getName() != null)
                .filter(s -> !"SOFT".equalsIgnoreCase(s.getSkillType()))
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", s.getId().toString());
                    item.put("name", s.getName());
                    return item;
                })
                .collect(Collectors.toList());

        String catalogJson = mapper.writeValueAsString(catalogData);

        String cvList = cvSkills.isEmpty() ? "Aucune" : String.join(", ", cvSkills);

        String userGoal = (objective + " " + offer).trim();

        String prompt = """
                Tu es un expert en recrutement technique.

                PROFIL DU CANDIDAT (issu de son CV) :
                %s

                COMPÉTENCES TECHNIQUES DÉJÀ PRÉSENTES DANS LE CV :
                %s

                OBJECTIF OU OFFRE DÉCLARÉS (facultatif, indicatif) :
                %s

                CATALOGUE DES COMPÉTENCES TECHNIQUES EXISTANTES (id, name) :
                %s

                TÂCHE :
                1. Déduis le métier visé principalement à partir du PROFIL, des
                   formations, des expériences et des compétences du CV.
                   Si un objectif ou une offre est fourni, utilise-le pour préciser
                   le métier ; s'il est absent, ne t'en préoccupe pas.
                2. Liste entre 5 et 12 compétences TECHNIQUES indispensables à ce métier
                   (langages, frameworks, outils, bases de données, méthodes, logiciels métier).
                3. N'inclus AUCUNE compétence comportementale (communication, rigueur,
                   travail d'équipe, leadership, etc.).
                4. Décris ce que le métier exige, pas seulement ce que le candidat
                   maîtrise déjà : la liste ne doit pas se limiter aux compétences du CV.
                5. Si une compétence du catalogue a le même sens (même formulée autrement
                   ou dans une autre langue), mets son id exact dans existingSkillId.
                6. Si une compétence du CV correspond à une compétence requise, reprends
                   exactement le nom du CV dans "name" pour éviter les doublons.
                7. Sinon existingSkillId = "" et propose un nom standard court (1 à 3 mots)
                   avec une catégorie technique (Backend, Base de données, DevOps,
                   Frontend, Comptabilité, etc.).
                8. N'invente jamais d'id. Pas de doublons ni de synonymes.
                9. requiredLevel : entier de 1 à 100
                   (débutant 30-40, intermédiaire 50-70, avancé 80 et plus).
                10. required = true si la compétence est essentielle, false si complémentaire.
                11. skillType = "TECHNICAL" toujours.

                Réponds uniquement avec le JSON demandé.
                """.formatted(
                cvContext.isBlank() ? "Non disponible" : cvContext,
                cvList,
                userGoal.isBlank() ? "Non renseigné" : userGoal,
                catalogJson
        );

        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "format", mapper.readTree(SCHEMA),
                "keep_alive", "10m",
                "options", Map.of(
                        "temperature", 0,
                        "num_ctx", 8192,
                        "num_predict", 2500
                )
        );

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(ollamaUrl + "/api/generate"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body)))
                .build();

        for (int attempt = 1; attempt <= 2; attempt++) {

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Ollama HTTP " + response.statusCode()
                                + " (modèle '" + model + "' téléchargé ?)");
            }

            try {
                String content = mapper.readTree(response.body())
                        .path("response").asText();

                JsonNode result = mapper.readTree(content);

                if (isValidResult(result)) {
                    JsonNode trimmed = trim(result);
                    log.info("Métier identifié par Ollama : {} | {} compétences",
                            trimmed.path("jobTitle").asText(),
                            trimmed.path("skills").size());
                    return trimmed;
                }

                log.warn("Réponse Ollama invalide, essai {}/2 : {}",
                        attempt,
                        content.length() > 400 ? content.substring(0, 400) + "..." : content);

            } catch (JsonProcessingException e) {
                log.warn("JSON invalide, essai {}/2 : {}", attempt, e.getMessage());
            }
        }

        throw new IllegalStateException(
                "Réponse JSON invalide pour l'analyse du métier.");
    }

    /** Garde au plus MAX_SKILLS compétences au lieu de rejeter toute la réponse. */
    private JsonNode trim(JsonNode result) {
        ArrayNode skills = (ArrayNode) result.path("skills");
        if (skills.size() <= MAX_SKILLS) {
            return result;
        }
        ObjectNode copy = mapper.createObjectNode();
        copy.put("jobTitle", result.path("jobTitle").asText());
        ArrayNode limited = copy.putArray("skills");
        for (int i = 0; i < MAX_SKILLS; i++) {
            limited.add(skills.get(i));
        }
        return copy;
    }

    private boolean isValidResult(JsonNode result) {

        if (!result.isObject()
                || result.path("jobTitle").asText().isBlank()
                || !result.path("skills").isArray()) {
            return false;
        }

        JsonNode skills = result.path("skills");

        if (skills.size() < MIN_SKILLS) {
            return false;
        }

        for (JsonNode skill : skills) {

            int level = skill.path("requiredLevel").asInt(-1);
            String type = skill.path("skillType").asText("");

            if (skill.path("name").asText().isBlank()
                    || skill.path("category").asText().isBlank()
                    || level < 1
                    || level > 100
                    || !skill.path("required").isBoolean()
                    || !type.equals("TECHNICAL")
                    || !skill.path("existingSkillId").isTextual()) {
                return false;
            }
        }

        return true;
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }
}