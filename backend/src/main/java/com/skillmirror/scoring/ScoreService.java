package com.skillmirror.scoring;

import com.skillmirror.assessment.Assessment;
import com.skillmirror.assessment.AssessmentRepository;
import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final CandidateScoreRepository scoreRepo;
    private final CandidateSkillLevelRepository levelRepo;
    private final CandidateProfileRepository profileRepo;
    private final AssessmentRepository assessmentRepo;
    private final SkillRepository skillRepo;
    private final CvSnapshotLoader cvLoader;
    private final RecommendationService recoService;

    /**
     * Calcul unique, appelé :
     *  - après la confirmation du CV        -> source "INITIAL"
     *  - après la fin d'une évaluation      -> source "ASSESSMENT"
     */
    @Transactional
    public CandidateScore recompute(UUID profileId, String source) {
        var cv = cvLoader.load(profileId);

        double cvScore = computeCvScore(cv);
        double formScore = computeFormScore(profileId);

        Map<UUID, Integer> assessed = latestAssessmentLevels(profileId);
        rebuildSkillLevels(profileId, cv, assessed);                       // point 2

        double assessScore = assessed.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double global = assessed.isEmpty()
                ? cvScore * 0.6 + formScore * 0.4                          // point 1
                : cvScore * 0.4 + formScore * 0.2 + assessScore * 0.4;     // points 6 et 7

        CandidateScore s = new CandidateScore();
        s.setProfileId(profileId);
        s.setCvScore(round(cvScore));
        s.setFormScore(round(formScore));
        s.setAssessmentScore(round(assessScore));
        s.setGlobalScore(round(global));
        s.setSource(source);
        scoreRepo.save(s);

        recoService.generate(profileId);                                   // points 5 et 7
        return s;
    }

    private double computeCvScore(CvSnapshotLoader.CvSnapshot cv) {
        double skills = Math.min(cv.skills().size() / 10.0, 1) * 40;
        double exp    = Math.min(cv.experienceYears() / 5.0, 1) * 25;
        double edu    = cv.educationCount() > 0 ? 15 : 0;
        double lang   = Math.min(cv.languageCount() / 3.0, 1) * 10;
        double cert   = Math.min(cv.certifications().size() / 2.0, 1) * 10;
        return skills + exp + edu + lang + cert;
    }

    private double computeFormScore(UUID profileId) {
        CandidateProfile p = profileRepo.findByProfileId(profileId).orElse(null);
        if (p == null) return 0;
        int filled = 0, total = 13;
        if (p.getAge() != null) filled++;
        if (nb(p.getGender())) filled++;
        if (nb(p.getCountry())) filled++;
        if (nb(p.getRegion())) filled++;
        if (nb(p.getStudyField())) filled++;
        if (nb(p.getEducationLevel())) filled++;
        if (nb(p.getDiploma())) filled++;
        if (nb(p.getSector())) filled++;
        if (nb(p.getAvailability())) filled++;
        if (nb(p.getInterviewLanguage())) filled++;
        if (p.getInterests() != null && !p.getInterests().isEmpty()) filled++;
        if (nb(p.getTargetOfferDescription()) || nb(p.getTrainingObjective())) filled++;
        if (nb(p.getCvStoragePath())) filled++;
        return filled * 100.0 / total;
    }

    /** Dernière évaluation terminée par compétence (en %). */
    private Map<UUID, Integer> latestAssessmentLevels(UUID profileId) {
        Map<UUID, Assessment> latest = new HashMap<>();
        for (Assessment a : assessmentRepo.findByProfileIdAndStatus(profileId, "COMPLETED")) {
            if (a.getSkillId() == null || a.getScore() == null) continue;
            latest.merge(a.getSkillId(), a, (x, y) -> time(y).isAfter(time(x)) ? y : x);
        }
        Map<UUID, Integer> out = new HashMap<>();
        latest.forEach((k, a) -> out.put(k, (int) Math.round(percent(a))));
        return out;
    }

    private Instant time(Assessment a) { return a.getFinishedAt() != null ? a.getFinishedAt() : Instant.EPOCH; }

    /** À ADAPTER si Assessment.score n'est pas un pourcentage. */
    private double percent(Assessment a) {
        double s = a.getScore();
        return Math.max(0, Math.min(100, s <= 1.0 ? s * 100 : s));
    }

    /** Niveau CV (max 60) remplacé par le résultat d'évaluation quand il existe. */
    private void rebuildSkillLevels(UUID profileId, CvSnapshotLoader.CvSnapshot cv, Map<UUID, Integer> assessed) {
        Map<String, Skill> byName = new HashMap<>();
        for (Skill s : skillRepo.findAll()) {
            if (s.getName() != null) byName.putIfAbsent(norm(s.getName()), s);
        }

        Map<UUID, Integer> levels = new HashMap<>();
        Map<UUID, String> sources = new HashMap<>();

        for (String name : cv.skills()) {
            Skill sk = byName.get(norm(name));
            if (sk == null) continue;
            levels.put(sk.getId(), estimateFromCv(cv, name));
            sources.put(sk.getId(), "CV");
        }
        assessed.forEach((id, lvl) -> { levels.put(id, lvl); sources.put(id, "ASSESSMENT"); });

        levelRepo.deleteByProfileId(profileId);
        levelRepo.flush();
        List<CandidateSkillLevel> rows = new ArrayList<>();
        levels.forEach((skillId, lvl) -> {
            CandidateSkillLevel l = new CandidateSkillLevel();
            l.setProfileId(profileId);
            l.setSkillId(skillId);
            l.setLevelScore(lvl);
            l.setLevelLabel(label(lvl));
            l.setSource(sources.get(skillId));
            rows.add(l);
        });
        levelRepo.saveAll(rows);
    }

    private int estimateFromCv(CvSnapshotLoader.CvSnapshot cv, String skill) {
        String k = skill.toLowerCase();
        int level = 25;
        long inExp = cv.experienceTexts().stream().filter(t -> t.toLowerCase().contains(k)).count();
        level += (int) Math.min(inExp * 10, 20);
        if (cv.certifications().stream().anyMatch(c -> c.toLowerCase().contains(k))) level += 10;
        if (cv.experienceYears() >= 3) level += 5;
        return Math.min(level, 60);
    }

    static String label(int level) {
        return level < 40 ? "DEBUTANT" : level < 70 ? "INTERMEDIAIRE" : "AVANCE";
    }

    static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
        return n.replaceAll("[^a-z0-9+#]", "");
    }

    private static boolean nb(String s) { return s != null && !s.isBlank(); }
    private static double round(double v) { return Math.round(v * 10) / 10.0; }
}