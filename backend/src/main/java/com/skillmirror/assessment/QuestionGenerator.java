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
 *
 * Chaque niveau comporte 5 listes de questions ; une liste doit contenir au moins 10 questions.
 */
@Service
@RequiredArgsConstructor
public class QuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerator.class);

    /** Nombre de listes par niveau, et taille minimale d'une liste. */
    public static final int LISTS_PER_LEVEL = 5;
    public static final int MIN_LIST_SIZE = 10;

    /** Un petit modèle (1b / 3b) respecte mieux les consignes avec peu de questions par appel. */
    private static final int MAX_PER_CALL = 3;
    /** Nombre maximum d'appels pour remplir un niveau (évite une boucle sans fin). */
    private static final int MAX_ROUNDS = 25;

    public record Result(List<Question> questions, int passagesUsed) {}

    private final OllamaClient ollama;
    private final KnowledgeStore store;
    private final QuestionRepository questions;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public static String levelLabel(String level) {
        return switch (level) {
            case "BEGINNER" -> "débutant : questions simples sur une seule notion (définition, "
                    + "vocabulaire, rôle d'un élément). Pas de code complexe, pas de cas limite";
            case "INTERMEDIATE" -> "intermédiaire : application sur un cas simple, comparaison de "
                    + "deux notions, lecture d'un court exemple, choix de la bonne méthode. "
                    + "Plus difficile qu'une simple définition";
            default -> "avancé : cas pratique combinant plusieurs notions, erreurs fréquentes, "
                    + "optimisation, cas limites, bonnes pratiques. Les mauvaises options doivent "
                    + "être plausibles";
        };
    }

    // ------------------------------------------------------------------
    // Listes de questions
    // ------------------------------------------------------------------

    /** Nombre de questions dans chacune des 5 listes d'une compétence et d'un niveau. */
    public Map<Integer, Long> listSizes(UUID skillId, String level) {
        Map<Integer, Long> sizes = new TreeMap<>();
        for (int i = 1; i <= LISTS_PER_LEVEL; i++) sizes.put(i, 0L);
        for (Question q : questions.findBySkillIdAndLevel(skillId, level)) {
            int n = q.getListNo() == null ? 1 : q.getListNo();
            if (n >= 1 && n <= LISTS_PER_LEVEL) sizes.merge(n, 1L, Long::sum);
        }
        return sizes;
    }

    /** Vrai si les 5 listes contiennent chacune au moins 10 questions. */
    public boolean isComplete(UUID skillId, String level) {
        return listSizes(skillId, level).values().stream().allMatch(c -> c >= MIN_LIST_SIZE);
    }

    /** La liste la moins remplie : c'est elle qu'on complète en premier. */
    private int targetList(UUID skillId, String level) {
        int best = 1;
        long bestCount = Long.MAX_VALUE;
        for (Map.Entry<Integer, Long> e : listSizes(skillId, level).entrySet()) {
            if (e.getValue() < bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Génération
    // ------------------------------------------------------------------

    /** Génération pour la liste la moins remplie (utilisée par le remplissage d'un niveau entier). */
    public Result generate(Skill skill, String level, int requested) {
        return generate(skill, level, requested, targetList(skill.getId(), level));
    }

    /** Génération pour UNE liste précise. Ne lève jamais d'exception. */
    public Result generate(Skill skill, String level, int requested, int listNo) {
        int count = Math.min(requested, MAX_PER_CALL);
        long t0 = System.currentTimeMillis();
        List<String> passages = retrieve(skill, level);
        if (passages.isEmpty()) {
            log.warn("RAG : aucun passage de cours pour « {} » : génération IA impossible.", skill.getName());
            return new Result(List.of(), 0);
        }
        log.info("RAG : génération lancée pour « {} » ({}, liste {}) : {} passage(s), modèle {}",
                skill.getName(), level, listNo, passages.size(), ollama.modelName());
        try {
            List<String> avoid = recentQuestionTexts(skill.getId(), level, 12);
            String json = ollama.generateJson(buildPrompt(skill, level, count, passages, avoid));
            List<Question> saved = parseAndSave(json, skill, level, count, listNo);
            log.info("RAG : {} question(s) générée(s) pour « {} » ({}, liste {}) en {} s",
                    saved.size(), skill.getName(), level, listNo, (System.currentTimeMillis() - t0) / 1000);
            return new Result(saved, passages.size());
        } catch (Exception e) {
            log.warn("RAG : génération IA impossible pour « {} » ({}) : {}",
                    skill.getName(), level, e.toString());
            return new Result(List.of(), passages.size());
        }
    }

    /** Complète en arrière-plan UNE seule liste jusqu'à `target` questions. */
    public void fillListAsync(Skill skill, String level, int listNo, int target) {
        String key = skill.getId() + ":" + level + ":" + listNo;
        if (!running.add(key)) {
            log.info("RAG : la liste {} de « {} » ({}) est déjà en cours de génération.",
                    listNo, skill.getName(), level);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                int failures = 0;
                for (int round = 0; round < MAX_ROUNDS; round++) {
                    long size = listSizes(skill.getId(), level).getOrDefault(listNo, 0L);
                    if (size >= target) {
                        log.info("RAG : liste {} de « {} » ({}) complète ({} questions).",
                                listNo, skill.getName(), level, size);
                        return;
                    }
                    Result r = generate(skill, level, (int) Math.min(MAX_PER_CALL, target - size), listNo);
                    failures = r.questions().isEmpty() ? failures + 1 : 0;
                    if (failures >= 2) {
                        log.warn("RAG : liste {} de « {} » ({}) arrêtée après 2 échecs.",
                                listNo, skill.getName(), level);
                        return;
                    }
                }
            } catch (Throwable t) {
                log.error("RAG : erreur pendant le remplissage de la liste", t);
            } finally {
                running.remove(key);
            }
        });
    }

    /**
     * Remplit en arrière-plan les 5 listes d'un niveau jusqu'à 10 questions chacune.
     * Un seul remplissage à la fois par compétence et niveau.
     */
    public void fillAsync(Skill skill, String level) {
        String key = skill.getId() + ":" + level;
        if (!running.add(key)) {
            log.info("RAG : un remplissage est déjà en cours pour « {} » ({}).", skill.getName(), level);
            return;
        }
        log.info("RAG : remplissage des listes lancé pour « {} » ({}).", skill.getName(), level);
        CompletableFuture.runAsync(() -> {
            try {
                fillLevel(skill, level);
            } catch (Throwable t) {
                log.error("RAG : erreur inattendue pendant le remplissage", t);
            } finally {
                running.remove(key);
            }
        });
    }

    /** Remplissage synchrone (utilisé par le pré-remplissage au démarrage). */
    public void fillNow(Skill skill, String level) {
        String key = skill.getId() + ":" + level;
        if (!running.add(key)) return;
        try {
            fillLevel(skill, level);
        } finally {
            running.remove(key);
        }
    }

    private void fillLevel(Skill skill, String level) {
        int failures = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (isComplete(skill.getId(), level)) {
                log.info("RAG : les 5 listes de « {} » ({}) sont complètes.", skill.getName(), level);
                return;
            }
            Result r = generate(skill, level, MAX_PER_CALL);
            failures = r.questions().isEmpty() ? failures + 1 : 0;
            if (failures >= 2) {
                log.warn("RAG : remplissage arrêté pour « {} » ({}) après 2 échecs consécutifs.",
                        skill.getName(), level);
                return;
            }
        }
    }

    // ---------- 1) RETRIEVE ----------
    private List<String> retrieve(Skill skill, String level) {
        try {
            float[] q = ollama.embed(skill.getName() + " " + levelLabel(level));
            List<String> found = store.search(skill.getId(), q, 4);
            if (!found.isEmpty()) return found;
            log.info("RAG : aucun passage indexé pour « {} », passages par défaut.", skill.getName());
        } catch (Exception e) {
            log.warn("RAG : recherche vectorielle indisponible ({}), passages par défaut.", e.toString());
        }
        return store.firstChunks(skill.getId(), 4);
    }

    // ---------- 2) AUGMENT ----------
    private String buildPrompt(Skill skill, String level, int count, List<String> passages,
                               List<String> avoid) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < passages.size(); i++) {
            ctx.append("[").append(i + 1).append("] ").append(passages.get(i)).append("\n");
        }
        StringBuilder known = new StringBuilder();
        if (!avoid.isEmpty()) {
            known.append("\nQuestions déjà existantes (ne les répète pas, pose-en d'autres) :\n");
            for (String a : avoid) known.append("- ").append(a).append("\n");
        }
        return """
                Tu es un examinateur technique. À partir UNIQUEMENT des extraits de cours ci-dessous,
                rédige %d questions à choix multiples en français sur la compétence « %s »,
                niveau %s.

                Règles :
                - exactement 4 options, toutes différentes, une seule est correcte ;
                - "correctIndex" est le NUMÉRO de la bonne réponse : 0, 1, 2 ou 3 ;
                - "explanation" est une explication courte basée sur les extraits ;
                - ne pose aucune question dont la réponse n'est pas dans les extraits ;
                - réponds UNIQUEMENT avec ce JSON, sans texte autour :
                {"questions":[{"question":"","options":["","","",""],"correctIndex":0,"explanation":""}]}
                %s
                EXTRAITS DE COURS :
                %s
                """.formatted(count, skill.getName(), levelLabel(level), known, ctx);
    }

    private List<String> recentQuestionTexts(UUID skillId, String level, int max) {
        List<Question> all = questions.findBySkillIdAndLevel(skillId, level);
        List<String> out = new ArrayList<>();
        for (int i = Math.max(0, all.size() - max); i < all.size(); i++) {
            String t = all.get(i).getQuestionText().trim();
            out.add(t.length() > 120 ? t.substring(0, 120) + "…" : t);
        }
        return out;
    }

    // ---------- 3) GENERATE : validation puis enregistrement ----------
    private List<Question> parseAndSave(String json, Skill skill, String level, int count, int listNo)
            throws Exception {
        JsonNode arr = extractArray(mapper.readTree(json));
        if (arr == null) {
            log.warn("RAG : réponse du modèle sans liste de questions : {}", excerpt(json));
            return List.of();
        }

        Set<String> existing = new HashSet<>();
        for (Question q : questions.findBySkillId(skill.getId())) {
            existing.add(q.getQuestionText().trim().toLowerCase());
        }

        List<Question> saved = new ArrayList<>();
        int rejected = 0;
        for (JsonNode n : arr) {
            if (saved.size() >= count) break;
            String text = n.path("question").asText("").trim();
            JsonNode opts = n.path("options");
            if (text.isEmpty() || !opts.isArray() || opts.size() != 4) { rejected++; continue; }

            List<String> options = new ArrayList<>();
            for (JsonNode o : opts) options.add(o.asText("").trim());
            if (options.stream().anyMatch(String::isEmpty) || new HashSet<>(options).size() != 4) {
                rejected++;
                continue;
            }

            int idx = parseIndex(n.path("correctIndex"), options);
            if (idx < 0 || idx > 3) { rejected++; continue; }
            if (!existing.add(text.toLowerCase())) { rejected++; continue; }   // pas de doublon

            Question q = new Question();
            q.setSkillId(skill.getId());
            q.setLevel(level);
            q.setListNo(listNo);
            q.setQuestionText(text);
            q.applyOptions(options);
            q.setCorrectIndex(idx);
            q.setExplanation(n.path("explanation").asText("").trim());
            q.setSource("AI");
            saved.add(questions.save(q));
        }
        if (saved.isEmpty()) {
            log.warn("RAG : 0 question valide ({} rejetée(s)). Réponse du modèle : {}", rejected, excerpt(json));
        }
        return saved;
    }

    /** Accepte {"questions":[...]}, un tableau seul, ou le premier tableau trouvé. */
    private static JsonNode extractArray(JsonNode root) {
        if (root == null) return null;
        if (root.isArray()) return root;
        JsonNode q = root.get("questions");
        if (q != null && q.isArray()) return q;
        for (Iterator<JsonNode> it = root.elements(); it.hasNext(); ) {
            JsonNode v = it.next();
            if (v.isArray()) return v;
        }
        return null;
    }

    /** Accepte 0-3, "2", une lettre A-D, ou le texte exact de la bonne option. */
    private static int parseIndex(JsonNode node, List<String> options) {
        if (node == null || node.isMissingNode() || node.isNull()) return -1;
        if (node.isNumber()) return node.asInt(-1);
        String s = node.asText("").trim();
        if (s.matches("\\d")) return Integer.parseInt(s);
        if (s.matches("(?i)[A-D]")) return Character.toUpperCase(s.charAt(0)) - 'A';
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(s)) return i;
        }
        return -1;
    }

    private static String excerpt(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ");
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }
}