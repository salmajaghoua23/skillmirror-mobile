package com.skillmirror.recruiter;

import com.skillmirror.common.Profile;
import com.skillmirror.common.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Logique métier du profil recruteur.
 * Spring Boot se connecte à la base avec un compte qui ignore la RLS :
 * c'est donc ICI que l'on vérifie que l'utilisateur a le droit d'agir.
 */
@Service
@RequiredArgsConstructor
public class RecruiterProfileService {

    private final ProfileRepository profiles;
    private final RecruiterProfileRepository recruiters;
    private final SectorRepository sectors;
    private final RecruitmentDomainRepository domains;

    private Profile recruiterAccount(String uid) {
        Profile p = profiles.findByFirebaseUid(uid).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));
        if (!"recruiter".equals(p.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public RecruiterProfileResponse get(String uid) {
        Profile p = recruiterAccount(uid);
        RecruiterProfile r = recruiters.findByProfileId(p.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil non renseigné"));
        return toResponse(p, r);
    }

    @Transactional
    public RecruiterProfileResponse save(String uid, RecruiterProfileRequest req) {
        Profile p = recruiterAccount(uid);
        RecruiterProfile r = recruiters.findByProfileId(p.getId()).orElseGet(() -> {
            RecruiterProfile n = new RecruiterProfile();
            n.setProfileId(p.getId());
            return n;
        });

        Sector sector = sectors.findById(req.sectorId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secteur invalide"));
        Set<RecruitmentDomain> found = new HashSet<>(domains.findAllById(req.domainIds()));
        if (found.size() != req.domainIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Domaine invalide");
        }

        r.setCompanyName(req.companyName().trim());
        r.setPosition(req.position().trim());
        r.setSector(sector);
        r.setCompanySize(req.companySize().trim());
        r.setCompanyWebsite(blankToNull(req.companyWebsite()));
        r.setCountry(req.country().trim());
        r.setCity(req.city().trim());
        r.setCompanyDescription(req.companyDescription().trim());
        r.setHiringNeeds(blankToNull(req.hiringNeeds()));
        r.setDomains(found);
        r.setUpdatedAt(Instant.now());
        recruiters.save(r);

        p.setFullName(req.fullName().trim());
        p.setOnboardingDone(true);
        profiles.save(p);
        return toResponse(p, r);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private RecruiterProfileResponse toResponse(Profile p, RecruiterProfile r) {
        return new RecruiterProfileResponse(
                p.getFullName(), p.getEmail(), r.getPosition(), r.getCompanyName(),
                r.getSector() == null ? null : new RefItem(r.getSector().getId(), r.getSector().getName()),
                r.getCompanySize(), r.getCompanyWebsite(), r.getCountry(), r.getCity(),
                r.getCompanyDescription(), r.getHiringNeeds(),
                r.getDomains().stream()
                        .map(d -> new RefItem(d.getId(), d.getName()))
                        .sorted(Comparator.comparing(RefItem::name))
                        .toList());
    }
}
