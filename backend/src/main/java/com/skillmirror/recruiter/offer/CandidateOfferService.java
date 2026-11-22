package com.skillmirror.recruiter.offer;

import com.skillmirror.candidate.service.CandidateAccess;
import com.skillmirror.common.Profile;
import com.skillmirror.recruiter.RecruiterProfileRepository;
import com.skillmirror.recruiter.offer.OfferDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Côté candidat (minimum nécessaire pour alimenter l'espace recruteur) :
 * voir les offres publiées, postuler, gérer le consentement.
 * L'écran Flutter correspondant relève de la Personne 1 (Phase 6 / 10).
 */
@Service
@RequiredArgsConstructor
public class CandidateOfferService {

    private final CandidateAccess access;
    private final JobOfferRepository offers;
    private final ApplicationRepository applications;
    private final RecruiterProfileRepository recruiters;
    private final CandidateConsentRepository consents;

    @Transactional(readOnly = true)
    public List<OfferResponse> published(String uid) {
        access.candidate(uid);
        return offers.findByStatusOrderByCreatedAtDesc("PUBLISHED").stream()
                .map(o -> new OfferResponse(o.getId(), o.getTitle(), o.getDescription(), o.getLocation(),
                        o.getContractType(), o.getExperienceLevel(), Boolean.TRUE.equals(o.getRemote()),
                        o.getSalaryRange(), o.getStatus(),
                        recruiters.findById(o.getRecruiterId()).map(r -> r.getCompanyName()).orElse(null),
                        o.getSkills().stream()
                                .map(s -> new SkillOut(s.getSkillName(), !Boolean.FALSE.equals(s.getRequired())))
                                .sorted(Comparator.comparing(SkillOut::name, String.CASE_INSENSITIVE_ORDER))
                                .toList(),
                        0, o.getCreatedAt(), o.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ApplicationResponse apply(String uid, UUID offerId, String message) {
        Profile me = access.candidate(uid);
        JobOffer o = offers.findById(offerId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
        if (!"PUBLISHED".equals(o.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette offre n'accepte plus de candidatures");
        }
        if (applications.existsByOfferIdAndCandidateProfileId(offerId, me.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà postulé à cette offre");
        }
        Application a = new Application();
        a.setOfferId(offerId);
        a.setCandidateProfileId(me.getId());
        a.setMessage(message == null || message.isBlank() ? null : message.trim());
        applications.save(a);
        return new ApplicationResponse(a.getId(), offerId, o.getTitle(), me.getId(),
                me.getFullName(), a.getStatus(), a.getMessage(), null, a.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public boolean consent(String uid) {
        Profile me = access.candidate(uid);
        return consents.findByProfileId(me.getId())
                .map(c -> Boolean.TRUE.equals(c.getShareWithRecruiters())).orElse(false);
    }

    @Transactional
    public boolean setConsent(String uid, boolean share) {
        Profile me = access.candidate(uid);
        CandidateConsent c = consents.findByProfileId(me.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Complétez d'abord votre profil candidat"));
        c.setShareWithRecruiters(share);
        consents.save(c);
        return share;
    }
}
