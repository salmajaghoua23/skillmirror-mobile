package com.skillmirror.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Appels à Ollama : génération de texte (JSON) et embeddings (vecteurs). */
@Component
public class OllamaClient {

    @Value("${ollama.url:http://localhost:11434}") private String url;
    @Value("${ollama.model:llama3.2:3b}") private String model;
    @Value("${ollama.embed-model:nomic-embed-text}") private String embedModel;
    @Value("${ollama.timeout-seconds:180}") private long timeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    /** Génère une réponse JSON à partir d'un prompt. */
    public String generateJson(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model, "prompt", prompt, "stream", false, "format", "json",
                "options", Map.of("temperature", 0.3));
        JsonNode root = post("/api/generate", body, timeoutSeconds);
        return root.get("response").asText();
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
            throw new IllegalStateException("Ollama HTTP " + res.statusCode() + " sur " + path);
        }
        return mapper.readTree(res.body());
    }
}