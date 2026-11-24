package com.skillmirror.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Appels à Ollama : génération de texte (JSON) et embeddings (vecteurs). */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    @Value("${ollama.url:http://localhost:11434}") private String url;
    @Value("${ollama.model:llama3.2:3b}") private String model;
    @Value("${ollama.embed-model:nomic-embed-text}") private String embedModel;
    @Value("${ollama.timeout-seconds:180}") private long timeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    public String modelName() {
        return model;
    }

    /**
     * Génère un JSON de questions.
     * 1re tentative : sortie contrainte par un schéma JSON (le modèle ne peut pas partir en boucle).
     * 2e tentative (si la 1re échoue) : mode "json" simple, avec plus de variété.
     */
    public String generateJson(String prompt) throws Exception {
        try {
            return generate(prompt, questionsSchema(), 0.6, 1.15);
        } catch (Exception first) {
            log.warn("Ollama : 1re tentative échouée ({}). Nouvelle tentative en mode JSON simple.",
                    first.getMessage());
            return generate(prompt, "json", 0.8, 1.2);
        }
    }

    private String generate(String prompt, Object format, double temperature, double repeatPenalty)
            throws Exception {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        options.put("top_p", 0.9);
        options.put("repeat_penalty", repeatPenalty);   // évite « token repeat limit reached »
        options.put("num_predict", 1200);               // borne la longueur de la réponse
        options.put("num_ctx", 4096);                   // la consigne + les extraits tiennent largement

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", format);
        body.put("options", options);

        JsonNode root = post("/api/generate", body, timeoutSeconds);
        return root.get("response").asText();
    }

    /** Schéma JSON : {"questions":[{question, options[4], correctIndex, explanation}]} */
    private static Map<String, Object> questionsSchema() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("type", "array");
        options.put("items", Map.of("type", "string"));
        options.put("minItems", 4);
        options.put("maxItems", 4);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", Map.of(
                "question", Map.of("type", "string"),
                "options", options,
                "correctIndex", Map.of("type", "integer"),
                "explanation", Map.of("type", "string")));
        item.put("required", List.of("question", "options", "correctIndex", "explanation"));

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("type", "array");
        questions.put("items", item);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("questions", questions));
        schema.put("required", List.of("questions"));
        return schema;
    }

    /** Transforme un texte en vecteur (768 valeurs avec nomic-embed-text). */
    public float[] embed(String text) throws Exception {
        JsonNode root = post("/api/embed", Map.of("model", embedModel, "input", text), 60);
        JsonNode e = root.get("embeddings").get(0);
        float[] v = new float[e.size()];
        for (int i = 0; i < v.length; i++) v[i] = (float) e.get(i).asDouble();
        return v;
    }

    /** Format texte attendu par pgvector : [0.1,0.2,...] */
    public static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private JsonNode post(String path, Object body, long seconds) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + path))
                .timeout(Duration.ofSeconds(seconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            String detail = res.body() == null ? "" : res.body();
            if (detail.length() > 200) detail = detail.substring(0, 200);
            throw new IllegalStateException(
                    "Ollama HTTP " + res.statusCode() + " sur " + path + " : " + detail);
        }
        return mapper.readTree(res.body());
    }
}