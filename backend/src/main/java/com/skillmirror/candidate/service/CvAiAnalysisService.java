package com.skillmirror.candidate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.dto.CvAnalysisData.*;
import com.skillmirror.common.progress.AnalysisProgressService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CvAiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CvAiAnalysisService.class);

    // Suivi de progression (sans effet si l'analyse n'est pas lancée par CvAnalysisJob)
    private final AnalysisProgressService progress;

    @Value("${ollama.enabled}") private boolean enabled;
    @Value("${ollama.url}") private String ollamaUrl;
    @Value("${ollama.model}") private String model;
    @Value("${ollama.timeout-seconds}") private long timeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public CvAnalysisData analyze(String cvText) {
        if (!enabled) {
            progress.update(90, "Analyse simplifiée (IA désactivée)…");
            return analyzeWithRules(cvText);
        }
        try {
            return analyzeWithOllama(cvText).normalized();
        } catch (Exception e) {
            log.warn("Ollama indisponible ou réponse invalide ({}), utilisation du mode de secours.", e.getMessage());
            progress.update(90, "IA indisponible, analyse simplifiée…");
            return analyzeWithRules(cvText);
        }
    }

    // ---------- 1) IA locale ----------
    private static final String RULES = """
        Règles générales :
        - Réponds uniquement avec un JSON conforme au schéma demandé.
        - N'invente rien : utilise seulement ce qui est écrit dans le CV.
        - Ignore les textes entre crochets [ ... ] : ce sont des champs non remplis.
        - Laisse "" quand une information est absente.
        - Écris les années au format "2024".
        - Ne répète jamais le même élément : chaque élément de liste apparaît une seule fois.
        """;

    private static final String TASK_SKILLS = """
        TÂCHE : extraire les compétences et un résumé.
        - technicalSkills : compétences métier et techniques (langages, logiciels, outils, méthodes, domaines
          comme comptabilité, fiscalité, SQL, Docker...). Un élément court par compétence (1 à 4 mots).
          Chaque compétence apparaît UNE SEULE FOIS : jamais de répétition ni de doublon.
          Maximum 30 compétences techniques.
          Inclus celles listées dans le CV et celles clairement utilisées dans les stages et projets.
          Pas de phrases, pas de langues parlées, pas de qualités personnelles.
        - softSkills : qualités personnelles citées dans le CV (ex : rigueur, esprit d'équipe). Maximum 8.
        - summary : 2 phrases en français décrivant le profil du candidat.
        """;

    private static final String TASK_BACKGROUND = """
        TÂCHE : extraire formations, certifications et langues.
        - education : uniquement les diplômes et cursus (pas les stages ni les projets).
          degree = intitulé du diplôme sans le nom de l'école ; institution = école, faculté ou université ;
          field = spécialité.
          Exemple : {"degree":"Licence en Économie","institution":"Université Hassan II","field":"Économie","startYear":"2020","endYear":"2023"}
        - certifications : certificats et attestations obtenus (jamais un titre de section).
        - languages : langues parlées, avec le niveau tel qu'écrit dans le CV (B2, courant, natif...).
          Exemple : {"name":"Français","level":"B2"}
        """;

    private static final String TASK_EXPERIENCES = """
        TÂCHE : extraire les expériences.
        - experiences : stages, emplois et projets (un élément chacun).
          title = intitulé du poste ou du projet ; company = entreprise ou organisme ("" pour un projet personnel) ;
          startDate et endDate = dates telles qu'écrites dans le CV ; description = une phrase sur les missions (25 mots max).
          Exemple : {"title":"Stagiaire développeur","company":"Délégation de la Santé","startDate":"Juil 2025","endDate":"Août 2025","description":"Développement d'une application web de suivi des vaccinations."}
        """;

    private static final String SCHEMA_SKILLS = """
        {"type":"object","properties":{
          "technicalSkills":{"type":"array","maxItems":30,"items":{"type":"string"}},
          "softSkills":{"type":"array","maxItems":10,"items":{"type":"string"}},
          "summary":{"type":"string"}},
         "required":["technicalSkills","softSkills","summary"]}
        """;

    private static final String SCHEMA_BACKGROUND = """
        {"type":"object","properties":{
          "education":{"type":"array","maxItems":10,"items":{"type":"object","properties":{
            "degree":{"type":"string"},"institution":{"type":"string"},"field":{"type":"string"},
            "startYear":{"type":"string"},"endYear":{"type":"string"}},
            "required":["degree","institution"]}},
          "certifications":{"type":"array","maxItems":10,"items":{"type":"object","properties":{
            "name":{"type":"string"},"issuer":{"type":"string"},"year":{"type":"string"}},
            "required":["name"]}},
          "languages":{"type":"array","maxItems":8,"items":{"type":"object","properties":{
            "name":{"type":"string"},"level":{"type":"string"}},"required":["name"]}}},
         "required":["education","certifications","languages"]}
        """;

    private static final String SCHEMA_EXPERIENCES = """
        {"type":"object","properties":{
          "experiences":{"type":"array","maxItems":15,"items":{"type":"object","properties":{
            "title":{"type":"string"},"company":{"type":"string"},"startDate":{"type":"string"},
            "endDate":{"type":"string"},"description":{"type":"string"}},
            "required":["title","company"]}}},
         "required":["experiences"]}
        """;

    private CvAnalysisData analyzeWithOllama(String cvText) throws Exception {
        String text = cvText.length() > 15000 ? cvText.substring(0, 15000) : cvText;

        ObjectNode merged = mapper.createObjectNode();

        progress.update(20, "Analyse des compétences…");
        merged.setAll(ask("compétences", text, TASK_SKILLS, SCHEMA_SKILLS, 700));

        progress.update(50, "Analyse des formations…");
        merged.setAll(ask("formations", text, TASK_BACKGROUND, SCHEMA_BACKGROUND, 1000));

        progress.update(75, "Analyse des expériences…");
        merged.setAll(ask("expériences", text, TASK_EXPERIENCES, SCHEMA_EXPERIENCES, 1200));

        progress.update(95, "Enregistrement…");
        return mapper.treeToValue(merged, CvAnalysisData.class);
    }

    /**
     * Un appel Ollama ciblé. Un seul nouvel essai, avec d'autres paramètres :
     * relancer exactement la même requête redonnerait la même réponse défaillante.
     */
    private ObjectNode ask(String name, String text, String task, String schema, int maxTokens) throws Exception {
        String prompt = "Voici le CV d'un candidat (tous secteurs possibles) :\n\n<cv>\n" + text
                + "\n</cv>\n\n" + RULES + "\n" + task;

        for (int attempt = 1; attempt <= 2; attempt++) {

            // essai 1 : quasi déterministe ; essai 2 : plus de variété pour casser une boucle de répétition
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("temperature", attempt == 1 ? 0.1 : 0.4);
            options.put("repeat_penalty", attempt == 1 ? 1.2 : 1.35);
            options.put("repeat_last_n", 256);
            options.put("top_k", 40);
            options.put("top_p", 0.9);
            options.put("seed", 42 + attempt);
            options.put("num_ctx", 8192);
            options.put("num_predict", maxTokens);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false);
            body.put("format", mapper.readTree(schema));
            body.put("keep_alive", "10m");
            body.put("options", options);

            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + res.statusCode() + " (modèle '" + model + "' téléchargé ?)");
            }
            try {
                String json = mapper.readTree(res.body()).get("response").asText();
                log.info("Ollama [{}] essai {} -> {}", name, attempt,
                        json.length() > 600 ? json.substring(0, 600) + "..." : json);
                JsonNode node = mapper.readTree(json);
                if (node.isObject()) return (ObjectNode) node;
            } catch (JsonProcessingException e) {
                log.warn("Ollama [{}] : JSON invalide (essai {}/2)", name, attempt);
            }
        }
        throw new IllegalStateException("réponse JSON invalide pour « " + name + " »");
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