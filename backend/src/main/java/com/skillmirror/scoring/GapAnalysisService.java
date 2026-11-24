package com.skillmirror.scoring;

import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.skills.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GapAnalysisService {

    private final CandidateProfileRepository profileRepo;
    private final CandidateSkillLevelRepository levelRepo;
    private final JobRoleRepository roleRepo;
    private final JobRoleSkillRepository roleSkillRepo;
    private final SkillRepository skillRepo;

    static final int REQUIRED_LEVEL = 70, OPTIONAL_LEVEL = 50;

    /** Métier visé = nom de métier (job_roles.name) trouvé dans l'objectif du candidat. */
    private Optional<JobRole> targetRole(UUID profileId) {
        CandidateProfile p = profileRepo.findByProfileId(profileId).orElse(null);
        if (p == null) return Optional.empty();
        String goal = ScoreService.norm(nz(p.getTrainingObjective()) + " " + nz(p.getTargetOfferDescription()));
        return roleRepo.findAll().stream()
                .filter(r -> r.getName() != null && !ScoreService.norm(r.getName()).isEmpty()
                        && goal.contains(ScoreService.norm(r.getName())))
                .max(Comparator.comparingInt(r -> r.getName().length())); // le nom le plus précis
    }

    private Map<UUID, String> skillNames(Collection<UUID> ids) {
        return skillRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Skill::getId, s -> s.getName() == null ? "?" : s.getName()));
    }

    private Map<UUID, Integer> myLevels(UUID profileId) {
        return levelRepo.findByProfileId(profileId).stream()
                .collect(Collectors.toMap(
                        CandidateSkillLevel::getSkillId,
                        CandidateSkillLevel::getLevelScore,
                        (a, b) -> a
                ));
    }

    private int need(JobRoleSkill rs) {
        return Boolean.FALSE.equals(rs.getRequired()) ? OPTIONAL_LEVEL : REQUIRED_LEVEL;
    }

    public List<ScoringDtos.MissingSkillDto> findMissingSkills(UUID profileId) {
        var role = targetRole(profileId);
        if (role.isEmpty()) return List.of();

        List<JobRoleSkill> req = roleSkillRepo.findByRoleId(role.get().getId());
        Map<UUID, String> names = skillNames(req.stream().map(JobRoleSkill::getSkillId).toList());
        Map<UUID, Integer> mine = myLevels(profileId);

        List<ScoringDtos.MissingSkillDto> out = new ArrayList<>();
        for (JobRoleSkill rs : req) {
            int need = need(rs);
            Integer cur = mine.get(rs.getSkillId());
            int current = cur == null ? 0 : cur;
            if (current < need) {
                out.add(new ScoringDtos.MissingSkillDto(rs.getSkillId(),
                        names.getOrDefault(rs.getSkillId(), "?"), need, current,
                        cur == null ? "ABSENTE" : "INSUFFISANTE"));
            }
        }
        out.sort(Comparator.comparingInt((ScoringDtos.MissingSkillDto m) -> m.requiredLevel() - m.currentLevel()).reversed());
        return out;
    }

    public ScoringDtos.GoalComparisonDto compareWithGoal(UUID profileId) {
        var role = targetRole(profileId);
        if (role.isEmpty())
            return new ScoringDtos.GoalComparisonDto("Objectif non défini", 0, List.of(), List.of());

        List<JobRoleSkill> req = roleSkillRepo.findByRoleId(role.get().getId());
        Map<UUID, String> names = skillNames(req.stream().map(JobRoleSkill::getSkillId).toList());
        Map<UUID, Integer> mine = myLevels(profileId);

        double total = 0;
        List<ScoringDtos.SkillLevelDto> strengths = new ArrayList<>();
        for (JobRoleSkill rs : req) {
            int need = need(rs);
            int lvl = mine.getOrDefault(rs.getSkillId(), 0);
            total += Math.min(lvl / (double) need, 1.0);
            if (lvl >= need) {
                strengths.add(new ScoringDtos.SkillLevelDto(rs.getSkillId(),
                        names.getOrDefault(rs.getSkillId(), "?"), lvl, ScoreService.label(lvl), need));
            }
        }
        double pct = req.isEmpty() ? 0 : total * 100.0 / req.size();
        return new ScoringDtos.GoalComparisonDto(role.get().getName(),
                Math.round(pct * 10) / 10.0, strengths, findMissingSkills(profileId));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}