package com.skillmirror.candidate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.dto.CvAnalysisData.*;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CvAiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CvAiAnalysisService.class);

    @Value("${ollama.url:http://localhost:11434}") private String ollamaUrl;
    @Value("${ollama.model:llama3.2:1b}") private String model;
    @Value("${ollama.timeout-seconds:240}") private long timeoutSeconds;
    @Value("${ollama.enabled:true}") private boolean enabled;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public CvAnalysisData analyze(String cvText) {
        if (!enabled) {
            return analyzeWithRules(cvText);
        }
        try {
            return analyzeWithOllama(cvText).normalized();
        } catch (Exception e) {
            log.warn("Ollama indisponible ou réponse invalide ({}), utilisation du mode de secours.", e.getMessage());
            return analyzeWithRules(cvText);
        }
    }

    // ---------- 1) IA locale ----------
    private CvAnalysisData analyzeWithOllama(String cvText) throws Exception {
        String text = cvText.length() > 4000 ? cvText.substring(0, 4000) : cvText;
        String prompt = """
                Tu es un expert en analyse de CV. Extrais les informations du CV ci-dessous.
                Réponds UNIQUEMENT avec un JSON valide, sans texte autour, avec exactement cette structure :
                {
                  "technicalSkills": ["..."],
                  "softSkills": ["..."],
                  "education": [{"degree":"","institution":"","field":"","startYear":"","endYear":""}],
                  "experiences": [{"title":"","company":"","startDate":"","endDate":"","description":""}],
                  "languages": [{"name":"","level":""}],
                  "certifications": [{"name":"","issuer":"","year":""}],
                  "summary": "résumé du profil en 2 phrases"
                }
                Règles : n'invente rien, laisse "" si l'information est absente, compétences en 1 à 3 mots.

                CV :
                """ + text;

        Map<String, Object> body = Map.of(
                "model", model, "prompt", prompt, "stream", false, "format", "json",
                "keep_alive", "30m",
                "options", Map.of("temperature", 0, "num_predict", 800, "num_ctx", 4096));

        HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/generate"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

        JsonNode root = mapper.readTree(res.body());
        return mapper.readValue(root.get("response").asText(), CvAnalysisData.class);
    }

    // ---------- 2) Secours sans IA ----------
    private static final List<String> TECH = List.of(
            "Java", "Spring Boot", "Spring", "Python", "JavaScript", "TypeScript", "React", "Flutter", "Dart",
            "Angular", "Vue", "Node.js", "SQL", "PostgreSQL", "MySQL", "MongoDB", "Docker", "Kubernetes", "Git",
            "GitHub", "Linux", "C++", "C#", "PHP", "Laravel", "FastAPI", "Django", "Flask", "PyTorch",
            "TensorFlow", "scikit-learn", "OpenCV", "MediaPipe", "Pandas", "NumPy", "JavaFX", "HTML", "CSS",
            "REST", "Firebase", "Supabase", "UML", "Matlab", "Power BI", "Excel", "CI/CD", "Hibernate", "Maven");
    private static final List<String> SOFT = List.of(
            "travail d'équipe", "leadership", "communication", "autonomie", "gestion du temps",
            "esprit d'analyse", "créativité", "adaptabilité", "résolution de problèmes", "rigueur",
            "teamwork", "problem solving", "time management", "adaptability", "critical thinking");
    private static final List<String> LANGS = List.of(
            "Français", "Anglais", "Arabe", "Espagnol", "Allemand", "Italien", "Amazigh",
            "French", "English", "Arabic", "Spanish", "German");
    private static final Pattern LEVEL = Pattern.compile(
            "\\b([ABC][12]|natif|native|courant|fluent|intermédiaire|intermediate|débutant|beginner|bilingue)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEARS = Pattern.compile("(20\\d{2}|19\\d{2})");

    private CvAnalysisData analyzeWithRules(String text) {
        String lower = text.toLowerCase();
        List<String> tech = new ArrayList<>();
        for (String s : TECH) {
            if (Pattern.compile("(?<![\\p{L}0-9])" + Pattern.quote(s.toLowerCase()) + "(?![\\p{L}0-9])")
                    .matcher(lower).find()) tech.add(s);
        }
        List<String> soft = new ArrayList<>();
        for (String s : SOFT) if (lower.contains(s)) soft.add(s);

        List<Language> langs = new ArrayList<>();
        for (String l : LANGS) {
            int i = lower.indexOf(l.toLowerCase());
            if (i >= 0) {
                String around = text.substring(i, Math.min(text.length(), i + 40));
                Matcher m = LEVEL.matcher(around);
                langs.add(new Language(l, m.find() ? m.group(1) : ""));
            }
        }

        List<Education> edu = new ArrayList<>();
        List<Certification> certs = new ArrayList<>();
        List<Experience> exps = new ArrayList<>();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.length() < 6 || t.length() > 160) continue;
            String lt = t.toLowerCase();
            Matcher y = YEARS.matcher(t);
            String year = y.find() ? y.group(1) : "";
            if (lt.matches(".*(licence|master|bachelor|ingénieur|cycle|diplôme|doctorat|bac\\s?\\+|baccalauréat|degree|engineering).*")) {
                edu.add(new Education(t, "", "", "", year));
            } else if (lt.matches(".*(certif|certificate|coursera|udemy|oracle certified|aws certified).*")) {
                certs.add(new Certification(t, "", year));
            } else if (lt.matches(".*(stage|stagiaire|internship|développeur|developer|engineer|ingénieur logiciel|freelance|projet de fin).*")) {
                exps.add(new Experience(t, "", "", year, ""));
            }
        }
        return new CvAnalysisData(tech, soft, edu, exps, langs, certs,
                "Analyse automatique simplifiée (IA locale indisponible) : vérifie et corrige les informations.");
    }
}