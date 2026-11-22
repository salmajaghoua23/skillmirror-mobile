package com.skillmirror.recruiter.offer;

import com.skillmirror.candidate.dto.CvAnalysisData;
import com.skillmirror.candidate.entity.CandidateCv;
import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.entity.CvAnalysis;
import com.skillmirror.candidate.repository.CandidateCvRepository;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.candidate.repository.CvAnalysisRepository;
import com.skillmirror.common.Profile;
import com.skillmirror.common.ProfileRepository;
import com.skillmirror.recruiter.offer.OfferDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Accès (lecture seule) aux données candidat pour l'espace recruteur.
 * Règle de confidentialité : un recruteur ne voit que les informations
 * de l'analyse CV que le candidat a CONFIRMÉES, et uniquement si le
 * candidat a consenti OU a postulé à l'une de ses offres.
 */
@Component
@RequiredArgsConstructor
public class CandidateDirectory {

    private final ProfileRepository profiles;
    private final CandidateProfileRepository candidateProfiles;
    private final CandidateCvRepository cvs;
    private final CvAnalysisRepository analyses;
    private final CandidateConsentRepository consents;

    /** Analyse CV confirmée du CV principal (ou du plus récent). */
    public Optional<CvAnalysisData> confirmedAnalysis(UUID profileId) {
        try {
            Optional<CandidateCv> cv = cvs.findFirstByProfileIdAndMainTrueOrderByUploadedAtDesc(profileId)
                    .or(() -> cvs.findFirstByProfileIdOrderByUploadedAtDesc(profileId));
            return cv.flatMap(c -> analyses.findByCvId(c.getId()))
                    .filter(a -> "CONFIRMED".equals(a.getStatus()))
                    .map(CvAnalysis::getData);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Compétences (techniques + comportementales) confirmées du candidat. */
    public Set<String> skillsOf(UUID profileId) {
        Set<String> out = new LinkedHashSet<>();
        confirmedAnalysis(profileId).ifPresent(d -> {
            out.addAll(d.technicalSkills());
            out.addAll(d.softSkills());
        });
        return out;
    }

    public boolean hasConsent(UUID profileId) {
        return consents.findByProfileId(profileId)
                .map(c -> Boolean.TRUE.equals(c.getShareWithRecruiters()))
                .orElse(false);
    }

    public List<UUID> consentingProfileIds() {
        return consents.findByShareWithRecruitersTrue().stream()
                .map(CandidateConsent::getProfileId).toList();
    }

    public Optional<Profile> candidateAccount(UUID profileId) {
        return profiles.findById(profileId).filter(p -> "candidate".equals(p.getRole()));
    }

    public CandidateSummary summary(Profile account, SkillMatcher.Result r) {
        CandidateProfile c = candidateProfiles.findByProfileId(account.getId()).orElse(null);
        return new CandidateSummary(
                account.getId(), account.getFullName(),
                c == null ? null : c.getSector(),
                c == null ? null : c.getCountry(),
                c == null ? null : c.getRegion(),
                c == null ? null : c.getEducationLevel(),
                c == null ? null : c.getAvailability(),
                r.score(), r.matched(), r.missing());
    }

    public CandidateDetail detail(Profile account, boolean includeEmail) {
        CandidateProfile c = candidateProfiles.findByProfileId(account.getId()).orElse(null);
        CvAnalysisData d = confirmedAnalysis(account.getId()).orElse(CvAnalysisData.empty());
        return new CandidateDetail(
                account.getId(), account.getFullName(),
                includeEmail ? account.getEmail() : null,
                c == null ? null : c.getCountry(),
                c == null ? null : c.getRegion(),
                c == null ? null : c.getStudyField(),
                c == null ? null : c.getEducationLevel(),
                c == null ? null : c.getDiploma(),
                c == null ? null : c.getSector(),
                c == null ? null : c.getAvailability(),
                c == null ? null : c.getMobility(),
                d.technicalSkills(), d.softSkills(),
                d.education().stream().map(e -> new EducationOut(
                        e.degree(), e.institution(), e.field(), e.startYear(), e.endYear())).toList(),
                d.experiences().stream().map(e -> new ExperienceOut(
                        e.title(), e.company(), e.startDate(), e.endDate(), e.description())).toList(),
                d.languages().stream().map(l -> new LanguageOut(l.name(), l.level())).toList(),
                d.certifications().stream().map(x -> new CertificationOut(
                        x.name(), x.issuer(), x.year())).toList(),
                d.summary());
    }
}
