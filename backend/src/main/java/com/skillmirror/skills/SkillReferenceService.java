package com.skillmirror.skills;

import com.skillmirror.skills.SkillDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;

/** Référentiel des compétences : domaines, compétences, métiers (lecture seule). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillReferenceService {

    private final SkillDomainRepository domains;
    private final SkillRepository skills;
    private final JobRoleRepository roles;
    private final JobRoleSkillRepository roleSkills;

    /** Tous les domaines avec leurs compétences (pour le sélecteur). */
    public List<DomainOut> domains() {
        Map<UUID, List<SkillOut>> byDomain = new HashMap<>();
        for (Skill s : skills.findAllByOrderByNameAsc()) {
            byDomain.computeIfAbsent(s.getDomainId(), k -> new ArrayList<>()).add(toOut(s));
        }
        return domains.findAllByOrderByNameAsc().stream()
                .map(d -> new DomainOut(d.getId(), d.getName(), d.getDescription(),
                        byDomain.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    /** Recherche par texte (sans accents ni casse) et/ou par domaine. */
    public List<SkillOut> search(String q, UUID domainId) {
        String needle = norm(q);
        return skills.findAllByOrderByNameAsc().stream()
                .filter(s -> domainId == null || domainId.equals(s.getDomainId()))
                .filter(s -> needle.isEmpty() || norm(s.getName()).contains(needle))
                .map(SkillReferenceService::toOut)
                .toList();
    }

    public List<RoleOut> roles() {
        return roles.findAllByOrderByNameAsc().stream()
                .map(r -> new RoleOut(r.getId(), r.getName(), r.getDescription(), r.getDomainId()))
                .toList();
    }

    /** Un métier avec les compétences nécessaires (obligatoires d'abord). */
    public RoleDetail role(UUID id) {
        JobRole r = roles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Métier introuvable"));
        Map<UUID, Skill> byId = new HashMap<>();
        skills.findAll().forEach(s -> byId.put(s.getId(), s));

        List<RoleSkillOut> list = new ArrayList<>();
        for (JobRoleSkill rs : roleSkills.findByRoleId(id)) {
            Skill s = byId.get(rs.getSkillId());
            if (s != null) {
                list.add(new RoleSkillOut(s.getId(), s.getName(), s.getSkillType(),
                        !Boolean.FALSE.equals(rs.getRequired())));
            }
        }
        list.sort(Comparator.comparing(RoleSkillOut::required).reversed()
                .thenComparing(RoleSkillOut::name, String.CASE_INSENSITIVE_ORDER));
        return new RoleDetail(r.getId(), r.getName(), r.getDescription(), list);
    }

    private static SkillOut toOut(Skill s) {
        return new SkillOut(s.getId(), s.getName(), s.getSkillType(), s.getDomainId());
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}