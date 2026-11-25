package com.skillmirror.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class CvContextBuilder {

    private final ObjectMapper mapper = new ObjectMapper();

    // Titres de sections qui marquent la fin du bloc « profil »
    private static final Pattern SECTION = Pattern.compile(
            "(?im)^\\s*(exp[ée]riences?|formations?|[ée]ducation|comp[ée]tences?|skills|projets?"
                    + "|langues?|certifications?|dipl[ôo]mes?|work experience|education)\\b");

    private static final Pattern NOISE = Pattern.compile(
            "(?i).*(@|https?://|linkedin|github|\\+?\\d[\\d .-]{7,}).*");

    /** Texte de la section « à propos / résumé professionnel » (début du CV avant la 1re section). */
    String extractProfile(String rawText, String aiSummary) {
        String text = rawText == null ? "" : rawText.trim();
        var m = SECTION.matcher(text);
        String head = (m.find() && m.start() > 40) ? text.substring(0, m.start()) : "";

        StringBuilder sb = new StringBuilder();
        for (String line : head.split("\\R")) {
            String l = line.trim();
            if (l.length() < 25) continue;            // ignore nom, titres courts
            if (NOISE.matcher(l).matches()) continue; // ignore email, téléphone, liens
            sb.append(l).append(' ');
        }
        String profile = sb.toString().trim();
        if (profile.length() > 700) profile = profile.substring(0, 700);

        // CV à deux colonnes ou texte désordonné : repli sur le résumé généré à l'extraction
        if (profile.length() < 60 && aiSummary != null) profile = aiSummary;
        return profile;
    }

    public String build(String rawText, String dataJson) {
        JsonNode d;
        try {
            d = mapper.readTree(dataJson == null ? "{}" : dataJson);
        } catch (Exception e) {
            d = mapper.createObjectNode();
        }

        StringBuilder ctx = new StringBuilder();

        String profile = extractProfile(rawText, d.path("summary").asText(""));
        if (!profile.isBlank()) ctx.append("PROFIL : ").append(profile).append("\n");

        ctx.append("FORMATIONS : ");
        for (JsonNode e : d.path("education")) {
            ctx.append(e.path("degree").asText("")).append(" ")
                    .append(e.path("field").asText("")).append("; ");
        }

        ctx.append("\nEXPÉRIENCES : ");
        for (JsonNode e : d.path("experiences")) {
            ctx.append(e.path("title").asText("")).append(" (")
                    .append(e.path("description").asText("")).append("); ");
        }

        return ctx.toString().trim();
    }

    /** Compétences techniques du JSON d'analyse du CV. */
    public List<String> technicalSkills(String dataJson) {
        List<String> out = new ArrayList<>();
        try {
            JsonNode d = mapper.readTree(dataJson == null ? "{}" : dataJson);
            for (JsonNode s : d.path("technicalSkills")) {
                String v = s.asText("").trim();
                if (!v.isBlank()) out.add(v);
            }
        } catch (Exception ignored) {
            // JSON illisible : liste vide
        }
        return out;
    }
}