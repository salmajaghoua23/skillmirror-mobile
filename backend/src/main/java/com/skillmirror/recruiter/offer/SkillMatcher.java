package com.skillmirror.recruiter.offer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calcul simple du taux de correspondance offre / candidat.
 * - compétence obligatoire = poids 2, optionnelle = poids 1
 * - score = poids des compétences que le candidat possède / poids total (0-100)
 * La comparaison est exacte après normalisation (casse, accents, espaces).
 * L'algorithme avancé reste celui de la Personne 1 (Phase 6).
 */
public final class SkillMatcher {

    private SkillMatcher() {}

    public record Result(int score, List<String> matched, List<String> missing) {}

    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** @param offerSkills nom de compétence -> obligatoire ? ; @param candidateSkills compétences du candidat */
    public static Result match(Map<String, Boolean> offerSkills, Set<String> candidateSkills) {
        Map<String, Boolean> wanted = new LinkedHashMap<>(offerSkills);
        Set<String> have = new java.util.HashSet<>();
        for (String c : candidateSkills) have.add(normalize(c));

        int total = 0, got = 0;
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : wanted.entrySet()) {
            int w = Boolean.FALSE.equals(e.getValue()) ? 1 : 2;
            total += w;
            if (have.contains(normalize(e.getKey()))) {
                got += w;
                matched.add(e.getKey());
            } else {
                missing.add(e.getKey());
            }
        }
        int score = total == 0 ? 0 : (int) Math.round(100.0 * got / total);
        return new Result(score, matched, missing);
    }
}
