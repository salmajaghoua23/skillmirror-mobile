package com.skillmirror.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmirror.skills.Skill;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Génération de questions avec RAG :
 *   1) RETRIEVE : on retrouve les passages de cours les plus proches de la compétence
 *   2) AUGMENT  : on les insère dans le prompt
 *   3) GENERATE : Ollama rédige des QCM en s'appuyant UNIQUEMENT sur ces passages
 */
@Service
@RequiredArgsConstructor
public class QuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerator.class);

    public record Result(List<Question> questions, int passagesUsed) {}

    private final OllamaClient ollama;
    private final KnowledgeStore store;
    private final QuestionRepository questions;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public static String levelLabel(String level) {
        return switch (level) {
            case "BEGINNER" -> "débutant (définitions et notions de base)";
            case "INTERMEDIATE" -> "intermédiaire (mise en pratique, comparaison de notions)";
            default -> "avancé (cas pratiques, pièges et bonnes pratiques)";
        };
    }

    /** Génération synchrone. Ne lève jamais d'exception : renvoie une liste vide en cas d'échec. */
    public Result generate(Skill skill, String level, int count) {
        List<String> passages = retrieve(skill, level);
        if (passages.isEmpty()) {
            log.info("RAG : aucun passage de cours pour « {} » : génération IA impossible.", skill.getName());
            return new Result(List.of(), 0);
        }
        try {
            String json = ollama.generateJson(buildPrompt(skill, level, count, passages));
            return new Result(parseAndSave(json, skill, level, count), passages.size());
        } catch (Exception e) {
            log.warn("Génération IA impossible ({}). La banque de questions est utilisée.", e.getMessage());
            return new Result(List.of(), passages.size());
        }
    }

    /** Génération en arrière-plan, sans doublon pour une même compétence et un même niveau. */
    public void generateAsync(Skill skill, String level, int count) {
        String key = skill.getId() + ":" + level;
        if (!running.add(key)) return;
        CompletableFuture.runAsync(() -> {
            try {
                generate(skill, level, count);
            } finally {
                running.remove(key);
            }
        });
    }

    // ---------- 1) RETRIEVE ----------
    private List<String> retrieve(Skill skill, String level) {
        try {
            float[] q = ollama.embed(skill.getName() + " " + levelLabel(level));
            List<String> found = store.search(skill.getId(), q, 4);
            if (!found.isEmpty()) return found;
        } catch (Exception e) {
            log.info("RAG : recherche vectorielle indisponible ({}), passages par défaut.", e.getMessage());
        }
        return store.firstChunks(skill.getId(), 4);
    }

    // ---------- 2) AUGMENT ----------
    private String buildPrompt(Skill skill, String level, int count, List<String> passages) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            ctx.append("[").append(i + 1).append("] ").append(passages.get(i)).append("\n");
        }
        return """
                Tu es un examinateur technique. À partir UNIQUEMENT des extraits de cours ci-dessous,
                rédige %d questions à choix multiples en français sur la compétence « %s »,
                niveau %s.

                Règles :
                - exactement 4 options, toutes différentes, une seule est correcte ;
                - "correctIndex" est la position de la bonne réponse : 0, 1, 2 ou 3 ;
                - "explanation" est une explication courte basée sur les extraits ;
                - ne pose aucune question dont la réponse n'est pas dans les extraits ;
                - réponds UNIQUEMENT avec ce JSON, sans texte autour :
                {"questions":[{"question":"","options":["","","",""],"correctIndex":0,"explanation":""}]}

                EXTRAITS DE COURS :
                %s
                """.formatted(count, skill.getName(), levelLabel(level), ctx);
    }

    // ---------- 3) GENERATE : validation puis enregistrement ----------
    private List<Question> parseAndSave(String json, Skill skill, String level, int count) throws Exception {
        JsonNode arr = mapper.readTree(json).get("questions");
        if (arr == null || !arr.isArray()) return List.of();

        Set<String> existing = new HashSet<>();
        for (Question q : questions.findBySkillIdAndLevel(skill.getId(), level)) {
            existing.add(q.getQuestionText().trim().toLowerCase());
        }

        List<Question> saved = new ArrayList<>();
        for (JsonNode n : arr) {
            if (saved.size() >= count) break;
            String text = n.path("question").asText("").trim();
            JsonNode opts = n.path("options");
            if (text.isEmpty() || !opts.isArray() || opts.size() != 4) continue;

            List<String> options = new ArrayList<>();
            for (JsonNode o : opts) options.add(o.asText("").trim());
            if (options.stream().anyMatch(String::isEmpty) || new HashSet<>(options).size() != 4) continue;

            int idx = n.path("correctIndex").asInt(-1);
            if (idx < 0 || idx > 3) continue;
            if (!existing.add(text.toLowerCase())) continue;   // pas de doublon

            Question q = new Question();
            q.setSkillId(skill.getId());
            q.setLevel(level);
            q.setQuestionText(text);
            q.applyOptions(options);
            q.setCorrectIndex(idx);
            q.setExplanation(n.path("explanation").asText("").trim());
            q.setSource("AI");
            saved.add(questions.save(q));
        }
        log.info("RAG : {} question(s) générée(s) pour « {} » ({})", saved.size(), skill.getName(), level);
        return saved;
    }
}