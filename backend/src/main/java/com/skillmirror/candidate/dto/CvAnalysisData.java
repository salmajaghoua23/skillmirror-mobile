package com.skillmirror.candidate.dto;

import java.util.ArrayList;
import java.util.List;

public record CvAnalysisData(
        List<String> technicalSkills,
        List<String> softSkills,
        List<Education> education,
        List<Experience> experiences,
        List<Language> languages,
        List<Certification> certifications,
        String summary) {

    public record Education(String degree, String institution, String field, String startYear, String endYear) {}
    public record Experience(String title, String company, String startDate, String endDate, String description) {}
    public record Language(String name, String level) {}
    public record Certification(String name, String issuer, String year) {}

    public static CvAnalysisData empty() {
        return new CvAnalysisData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "");
    }

    /** Remplace les null par des listes vides (sortie IA imprévisible). */
    public CvAnalysisData normalized() {
        return new CvAnalysisData(
                technicalSkills == null ? new ArrayList<>() : technicalSkills,
                softSkills == null ? new ArrayList<>() : softSkills,
                education == null ? new ArrayList<>() : education,
                experiences == null ? new ArrayList<>() : experiences,
                languages == null ? new ArrayList<>() : languages,
                certifications == null ? new ArrayList<>() : certifications,
                summary == null ? "" : summary);
    }
}