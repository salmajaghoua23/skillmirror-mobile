package com.skillmirror.candidate.service;

import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.candidate.repository.CandidateProfileRepository;
import com.skillmirror.account.AccountRepository;
import com.skillmirror.candidate.dto.*;
import com.skillmirror.common.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CandidateService {
    private final CandidateAccess access;
    private final CvService cvService;
    private final AccountRepository accountRepo;
    private final CandidateProfileRepository candidateRepo;

    private CandidateProfile findOrNew(Profile p) {
        return candidateRepo.findByProfileId(p.getId()).orElseGet(() -> {
            CandidateProfile c = new CandidateProfile();
            c.setProfileId(p.getId());
            return c;
        });
    }

    private void applyProfile(Profile p, CandidateProfile c, CandidateProfileRequest r) {
        p.setFullName(r.fullName().trim());
        c.setAge(r.age());
        c.setGender(r.gender());
        c.setCountry(r.country());
        c.setRegion(r.region());
        c.setStudyField(r.studyField());
        c.setEducationLevel(r.educationLevel());
        c.setDiploma(r.diploma());
    }

    private void applyPreferences(CandidateProfile c, PreferencesRequest r) {
        c.setSector(r.sector());
        c.setAvailability(r.availability());
        c.setMobility(Boolean.TRUE.equals(r.mobility()));
        c.setInterviewLanguage(r.interviewLanguage());
        c.setInterests(r.interests() == null ? new ArrayList<>() : new ArrayList<>(r.interests()));
        c.setTargetOfferDescription(r.targetOfferDescription());
        c.setTrainingObjective(r.trainingObjective());
    }

    public CandidateProfileResponse get(String uid) {
        Profile p = access.candidate(uid);
        CandidateProfile c = candidateRepo.findByProfileId(p.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil candidat non créé"));
        return CandidateProfileResponse.from(p, c);
    }

    @Transactional
    public CandidateProfileResponse onboarding(String uid, OnboardingRequest r) {
        Profile p = access.candidate(uid);
        CandidateProfile c = findOrNew(p);
        applyProfile(p, c, r.profile());
        applyPreferences(c, r.preferences());
        p.setOnboardingDone(true);
        accountRepo.save(p);
        CandidateProfile saved = candidateRepo.save(c);
        cvService.syncCandidateCvPath(p); // rattache le CV envoyé pendant l'inscription
        return CandidateProfileResponse.from(p, saved);
    }

    @Transactional
    public CandidateProfileResponse updateProfile(String uid, CandidateProfileRequest r) {
        Profile p = access.candidate(uid);
        CandidateProfile c = findOrNew(p);
        applyProfile(p, c, r);
        accountRepo.save(p);
        return CandidateProfileResponse.from(p, candidateRepo.save(c));
    }

    @Transactional
    public CandidateProfileResponse updatePreferences(String uid, PreferencesRequest r) {
        Profile p = access.candidate(uid);
        CandidateProfile c = findOrNew(p);
        applyPreferences(c, r);
        return CandidateProfileResponse.from(p, candidateRepo.save(c));
    }
}