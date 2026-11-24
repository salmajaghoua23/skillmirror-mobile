
package com.skillmirror.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String SCHEMA = """
        {
          "type": "object",
          "properties": {
            "jobTitle": {
              "type": "string"
            },
            "skills": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "name": {"type": "string"},
                  "existingSkillId": {"type": "string"},
                  "category": {"type": "string"},
                  "skillType": {
                    "type": "string",
                    "enum": ["TECHNICAL", "SOFT"]
                  },
                  "requiredLevel": {"type": "integer"},
                  "required": {"type": "boolean"}
                },
                "required": [
                  "name",
                  "existingSkillId",
                  "category",
                  "skillType",
                  "requiredLevel",
                  "required"
                ]
              }
            }
          },
          "required": ["jobTitle", "skills"]
        }
        """;

    public JsonNode analyze(
            String trainingObjective,
            String targetOfferDescription,
            List<Skill> catalog) {

        if (!enabled) {
            throw new IllegalStateException(
                    "L'analyse IA est désactivée."
            );
        }

        String objective = trainingObjective == null
                ? "" : trainingObjective.trim();

        String offer = targetOfferDescription == null
                ? "" : targetOfferDescription.trim();

        if (objective.isBlank() && offer.isBlank()) {
            throw new IllegalArgumentException(
                    "L'objectif professionnel est vide."
            );
        }

        try {
            return analyzeWithOllama(objective, offer, catalog);
        } catch (Exception e) {
            log.error(
                    "Erreur analyse métier Ollama : {}",
                    e.getMessage(),
                    e
            );

            throw new IllegalStateException(
                    "Impossible d'analyser l'objectif professionnel.",
                    e
            );
        }
    }

    private JsonNode analyzeWithOllama(
            String objective,
            String offer,
            List<Skill> catalog) throws Exception {

        List<Map<String, Object>> catalogData = catalog.stream()
                .filter(s -> s.getId() != null && s.getName() != null)
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", s.getId().toString());
                    item.put("name", s.getName());
                    item.put("category", s.getCategory());
                    item.put("skillType", s.getSkillType());
                    item.put("description", s.getDescription());
                    return item;
                })
                .collect(Collectors.toList());

        String catalogJson =
                mapper.writeValueAsString(catalogData);

        String prompt = """
                Tu es un expert en recrutement et en analyse des compétences professionnelles.

                OBJECTIF DU CANDIDAT :
                %s

                DESCRIPTION DE L'OFFRE :
                %s

                CATALOGUE GLOBAL DES COMPÉTENCES EXISTANTES :
                %s

                TÂCHE :

                1. Identifie le métier précis recherché.
                2. Génère entre 5 et 12 compétences réellement nécessaires
                   pour exercer ce métier.
                3. Pour chaque compétence générée, examine le catalogue fourni.
                4. Si une compétence du catalogue possède le même sens,
                   même si son nom est formulé différemment ou dans une autre langue,
                   retourne son ID exact dans existingSkillId.
                5. Ne crée pas une nouvelle compétence si une compétence
                   existante représente déjà le même savoir-faire.
                6. Si aucune compétence du catalogue ne correspond,
                   retourne une nouvelle appellation standardisée,
                   existingSkillId vide, une catégorie pertinente et son type.
                7. N'invente jamais un ID.
                8. Ne confonds pas deux compétences simplement parce qu'elles
                   appartiennent au même domaine.
                9. Ne reprends pas automatiquement les compétences du CV.
                10. requiredLevel est un entier entre 1 et 100.
                11. required=true pour une compétence essentielle.
                12. required=false pour une compétence complémentaire.
                13. skillType doit être TECHNICAL ou SOFT.
                14. category doit être une catégorie descriptive et pertinente.
                15. Évite les doublons et les synonymes dans le résultat.

                IMPORTANT :
                Le catalogue est la référence réelle de la base de données.
                Tu dois utiliser les IDs fournis uniquement lorsqu'une
                correspondance sémantique existe.

                Réponds uniquement avec le JSON demandé.
                """.formatted(
                objective,
                offer.isBlank() ? "Non renseignée" : offer,
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
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Ollama HTTP " + response.statusCode()
                );
            }

            try {
                String content = mapper.readTree(response.body())
                        .path("response")
                        .asText();

                JsonNode result = mapper.readTree(content);

                if (isValidResult(result)) {
                    log.info(
                            "Métier identifié par Ollama : {}",
                            result.path("jobTitle").asText()
                    );

                    return result;
                }

                log.warn(
                        "Réponse Ollama invalide, essai {}/2",
                        attempt
                );

            } catch (JsonProcessingException e) {
                log.warn(
                        "JSON invalide, essai {}/2 : {}",
                        attempt,
                        e.getMessage()
                );
            }
        }

        throw new IllegalStateException(
                "Réponse JSON invalide pour l'analyse du métier."
        );
    }

    private boolean isValidResult(JsonNode result) {

        if (!result.isObject()
                || result.path("jobTitle").asText().isBlank()
                || !result.path("skills").isArray()) {
            return false;
        }

        JsonNode skills = result.path("skills");

        if (skills.size() < 5 || skills.size() > 12) {
            return false;
        }

        for (JsonNode skill : skills) {

            int level =
                    skill.path("requiredLevel").asInt(-1);

            String type =
                    skill.path("skillType").asText("");

            if (skill.path("name").asText().isBlank()
                    || skill.path("category").asText().isBlank()
                    || level < 1
                    || level > 100
                    || !skill.path("required").isBoolean()
                    || !(type.equals("TECHNICAL")
                    || type.equals("SOFT"))
                    || !skill.path("existingSkillId").isTextual()) {
                return false;
            }
        }

        return true;
    }
}
