package com.skillmirror.recruiter.offer;

import com.skillmirror.common.Profile;
import com.skillmirror.common.ProfileRepository;
import com.skillmirror.recruiter.RecruiterProfile;
import com.skillmirror.recruiter.RecruiterProfileRepository;
import com.skillmirror.recruiter.offer.OfferDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Espace recruteur : offres, compétences, candidatures, candidats, dashboard.
 * Spring Boot ignore la RLS de Supabase : TOUTES les vérifications de droit
 * (rôle recruteur, propriétaire de l'offre, consentement) sont faites ici.
 */
@Service
@RequiredArgsConstructor
public class RecruiterOfferService {

    private final ProfileRepository profiles;
    private final RecruiterProfileRepository recruiters;
    private final JobOfferRepository offers;
    private final ApplicationRepository applications;
    private final CandidateDirectory directory;

    private record Ctx(Profile account, RecruiterProfile recruiter) {}

    private Ctx ctx(String uid) {
        Profile p = profiles.findByFirebaseUid(uid).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable"));
        if (!"recruiter".equals(p.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux recruteurs");
        }
        RecruiterProfile r = recruiters.findByProfileId(p.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Complétez d'abord votre profil recruteur"));
        return new Ctx(p, r);
    }

    private JobOffer ownedOffer(Ctx c, UUID offerId) {
        return offers.findByIdAndRecruiterId(offerId, c.recruiter().getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre introuvable"));
    }

    // ------------------------------------------------------------------
    // 1) Offres : créer / modifier / supprimer / consulter
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OfferResponse> list(String uid, String status) {
        Ctx c = ctx(uid);
        List<JobOffer> list = (status == null || status.isBlank())
                ? offers.findByRecruiterIdOrderByCreatedAtDesc(c.recruiter().getId())
                : offers.findByRecruiterIdAndStatusOrderByCreatedAtDesc(c.recruiter().getId(), status);
        return list.stream().map(o -> toResponse(o, c.recruiter().getCompanyName())).toList();
    }

    @Transactional(readOnly = true)
    public OfferResponse get(String uid, UUID id) {
        Ctx c = ctx(uid);
        return toResponse(ownedOffer(c, id), c.recruiter().getCompanyName());
    }

    @Transactional
    public OfferResponse create(String uid, OfferRequest req) {
        Ctx c = ctx(uid);
        JobOffer o = new JobOffer();
        o.setRecruiterId(c.recruiter().getId());
        apply(o, req);
        offers.save(o);
        return toResponse(o, c.recruiter().getCompanyName());
    }

    @Transactional
    public OfferResponse update(String uid, UUID id, OfferRequest req) {
        Ctx c = ctx(uid);
        JobOffer o = ownedOffer(c, id);
        apply(o, req);
        offers.save(o);
        return toResponse(o, c.recruiter().getCompanyName());
    }

    @Transactional
    public OfferResponse setStatus(String uid, UUID id, String status) {
        Ctx c = ctx(uid);
        JobOffer o = ownedOffer(c, id);
        o.setStatus(status);
        offers.save(o);
        return toResponse(o, c.recruiter().getCompanyName());
    }

    @Transactional
    public void delete(String uid, UUID id) {
        Ctx c = ctx(uid);
        offers.delete(ownedOffer(c, id));
    }

    private void apply(JobOffer o, OfferRequest req) {
        o.setTitle(req.title().trim());
        o.setDescription(req.description().trim());
        o.setLocation(blankToNull(req.location()));
        o.setContractType(req.contractType());
        o.setExperienceLevel(blankToNull(req.experienceLevel()));
        o.setRemote(Boolean.TRUE.equals(req.remote()));
        o.setSalaryRange(blankToNull(req.salaryRange()));
        o.setStatus(req.status() == null || req.status().isBlank() ? "DRAFT" : req.status());
        syncSkills(o, req.skills());
    }

    // ------------------------------------------------------------------
    // 2) Compétences demandées
    // ------------------------------------------------------------------

    /**
     * Synchronise la liste des compétences SANS tout supprimer/recréer
     * (sinon la contrainte unique offer_id + skill_name casserait).
     */
    private void syncSkills(JobOffer o, List<SkillInput> input) {
        Map<String, SkillInput> wanted = new LinkedHashMap<>();
        if (input != null) {
            for (SkillInput s : input) {
                String name = s.name().trim();
                if (!name.isEmpty()) {
                    wanted.putIfAbsent(name.toLowerCase(Locale.ROOT), new SkillInput(name, s.required()));
                }
            }
        }
        o.getSkills().removeIf(s -> !wanted.containsKey(s.getSkillName().toLowerCase(Locale.ROOT)));

        Map<String, JobOfferSkill> existing = new HashMap<>();
        for (JobOfferSkill s : o.getSkills()) existing.put(s.getSkillName().toLowerCase(Locale.ROOT), s);

        for (Map.Entry<String, SkillInput> e : wanted.entrySet()) {
            boolean required = !Boolean.FALSE.equals(e.getValue().required());
            JobOfferSkill s = existing.get(e.getKey());
            if (s == null) {
                s = new JobOfferSkill();
                s.setOffer(o);
                s.setSkillName(e.getValue().name());
                o.getSkills().add(s);
            }
            s.setRequired(required);
        }
    }

    // ------------------------------------------------------------------
    // 6) Candidatures
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApplicationResponse> applicationsOf(String uid, UUID offerId) {
        Ctx c = ctx(uid);
        JobOffer o = ownedOffer(c, offerId);
        Map<String, Boolean> wanted = skillMap(o);
        return applications.findByOfferIdOrderByCreatedAtDesc(o.getId()).stream()
                .map(a -> toApplication(a, o, wanted)).toList();
    }

    @Transactional
    public ApplicationResponse updateApplicationStatus(String uid, UUID applicationId, String status) {
        Ctx c = ctx(uid);
        Application a = applications.findById(applicationId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
        JobOffer o = ownedOffer(c, a.getOfferId());   // 404 si l'offre n'est pas la sienne
        a.setStatus(status);
        applications.save(a);
        return toApplication(a, o, skillMap(o));
    }

    // ------------------------------------------------------------------
    // 4) Candidats correspondants   5) Profils candidats autorisés
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CandidateSummary> matches(String uid, UUID offerId) {
        Ctx c = ctx(uid);
        JobOffer o = ownedOffer(c, offerId);
        Map<String, Boolean> wanted = skillMap(o);
        if (wanted.isEmpty()) return List.of();

        List<CandidateSummary> out = new ArrayList<>();
        for (UUID pid : directory.consentingProfileIds()) {
            directory.candidateAccount(pid).ifPresent(acc -> {
                SkillMatcher.Result r = SkillMatcher.match(wanted, directory.skillsOf(pid));
                if (r.score() > 0) out.add(directory.summary(acc, r));
            });
        }
        out.sort(Comparator.comparingInt(CandidateSummary::matchScore).reversed());
        return out.stream().limit(50).toList();
    }

    /** Profil détaillé : seulement si le candidat a consenti OU a postulé chez ce recruteur. */
    @Transactional(readOnly = true)
    public CandidateDetail candidateDetail(String uid, UUID candidateProfileId) {
        Ctx c = ctx(uid);
        Profile acc = directory.candidateAccount(candidateProfileId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidat introuvable"));
        boolean applied = applications.countByCandidateAndRecruiter(
                candidateProfileId, c.recruiter().getId()) > 0;
        if (!applied && !directory.hasConsent(candidateProfileId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce candidat n'a pas autorisé l'accès à son profil");
        }
        // L'e-mail n'est donné que si le candidat a postulé lui-même.
        return directory.detail(acc, applied);
    }

    // ------------------------------------------------------------------
    // 7) Dashboard
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(String uid) {
        Ctx c = ctx(uid);
        UUID rid = c.recruiter().getId();
        List<JobOffer> all = offers.findByRecruiterIdOrderByCreatedAtDesc(rid);
        List<Application> apps = applications.findAllForRecruiter(rid);

        Map<UUID, JobOffer> byId = new HashMap<>();
        all.forEach(o -> byId.put(o.getId(), o));

        List<ApplicationResponse> recent = apps.stream().limit(5)
                .map(a -> {
                    JobOffer o = byId.get(a.getOfferId());
                    return toApplication(a, o, o == null ? Map.of() : skillMap(o));
                }).toList();

        return new DashboardResponse(
                c.account().getFullName(), c.recruiter().getCompanyName(),
                all.size(),
                all.stream().filter(o -> "PUBLISHED".equals(o.getStatus())).count(),
                all.stream().filter(o -> "DRAFT".equals(o.getStatus())).count(),
                all.stream().filter(o -> "CLOSED".equals(o.getStatus())).count(),
                apps.size(),
                apps.stream().filter(a -> "SENT".equals(a.getStatus())).count(),
                apps.stream().filter(a -> "SHORTLISTED".equals(a.getStatus())).count(),
                apps.stream().filter(a -> "ACCEPTED".equals(a.getStatus())).count(),
                apps.stream().filter(a -> "REJECTED".equals(a.getStatus())).count(),
                recent);
    }

    // ------------------------------------------------------------------
    // Conversion vers les réponses
    // ------------------------------------------------------------------

    private static Map<String, Boolean> skillMap(JobOffer o) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        o.getSkills().forEach(s -> m.put(s.getSkillName(), !Boolean.FALSE.equals(s.getRequired())));
        return m;
    }

    private ApplicationResponse toApplication(Application a, JobOffer o, Map<String, Boolean> wanted) {
        String name = profiles.findById(a.getCandidateProfileId())
                .map(Profile::getFullName).orElse("Candidat");
        Integer score = wanted.isEmpty() ? null
                : SkillMatcher.match(wanted, directory.skillsOf(a.getCandidateProfileId())).score();
        return new ApplicationResponse(a.getId(), a.getOfferId(), o == null ? null : o.getTitle(),
                a.getCandidateProfileId(), name, a.getStatus(), a.getMessage(), score, a.getCreatedAt());
    }

    OfferResponse toResponse(JobOffer o, String companyName) {
        List<SkillOut> skills = o.getSkills().stream()
                .map(s -> new SkillOut(s.getSkillName(), !Boolean.FALSE.equals(s.getRequired())))
                .sorted(Comparator.comparing(SkillOut::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new OfferResponse(o.getId(), o.getTitle(), o.getDescription(), o.getLocation(),
                o.getContractType(), o.getExperienceLevel(), Boolean.TRUE.equals(o.getRemote()),
                o.getSalaryRange(), o.getStatus(), companyName, skills,
                o.getId() == null ? 0 : applications.countByOfferId(o.getId()),
                o.getCreatedAt(), o.getUpdatedAt());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
