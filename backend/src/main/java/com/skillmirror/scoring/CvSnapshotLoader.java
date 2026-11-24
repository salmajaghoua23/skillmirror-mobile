package com.skillmirror.scoring;

import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.entity.CandidateCv;
import com.skillmirror.candidate.entity.CvAnalysis;
import com.skillmirror.candidate.repository.CandidateCvRepository;
import com.skillmirror.candidate.repository.CvAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.*;
import java.util.regex.*;

@Component
@RequiredArgsConstructor
public class CvSnapshotLoader {

    public record CvSnapshot(List<String> skills, double experienceYears, List<String> experienceTexts,
                             int educationCount, int languageCount, List<String> certifications) {
        public static CvSnapshot empty() {
            return new CvSnapshot(List.of(), 0, List.of(), 0, 0, List.of());
        }
    }

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern NOW = Pattern.compile("(?i)pr[ée]sent|actuel|current|now|aujourd|en cours");

    private final CandidateCvRepository cvRepo;
    private final CvAnalysisRepository analysisRepo;

    public CvSnapshot load(UUID profileId) {
        Optional<CandidateCv> cv = cvRepo.findFirstByProfileIdAndMainTrueOrderByUploadedAtDesc(profileId)
                .or(() -> cvRepo.findFirstByProfileIdOrderByUploadedAtDesc(profileId));
        if (cv.isEmpty()) return CvSnapshot.empty();

        CvAnalysis analysis = analysisRepo.findByCvId(cv.get().getId()).orElse(null);
        // Seules les informations confirmées par le candidat comptent (phase 2, point 12)
        if (analysis == null || analysis.getConfirmedAt() == null) return CvSnapshot.empty();

        CvAnalysisData d = analysis.getData();
        if (d == null) return CvSnapshot.empty();

        List<String> skills = new ArrayList<>();
        skills.addAll(d.technicalSkills());
        skills.addAll(d.softSkills());

        List<String> expTexts = new ArrayList<>();
        double years = 0;
        for (CvAnalysisData.Experience e : d.experiences()) {
            expTexts.add(nz(e.title()) + " " + nz(e.company()) + " " + nz(e.description()));
            years += durationYears(e.startDate(), e.endDate());
        }
        years = Math.min(years, 40);

        List<String> certs = d.certifications().stream()
                .map(c -> nz(c.name())).filter(s -> !s.isBlank()).toList();

        return new CvSnapshot(skills, years, expTexts, d.education().size(), d.languages().size(), certs);
    }

    private double durationYears(String start, String end) {
        Integer s = firstYear(start);
        if (s == null) return 0;
        Integer e = (end != null && NOW.matcher(end).find()) ? Year.now().getValue() : firstYear(end);
        if (e == null) e = s + 1;
        return Math.max(e - s, 0.5);
    }

    private Integer firstYear(String text) {
        if (text == null) return null;
        Matcher m = YEAR.matcher(text);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}