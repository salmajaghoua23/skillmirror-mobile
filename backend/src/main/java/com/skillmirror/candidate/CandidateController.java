package com.skillmirror.candidate;

import com.skillmirror.account.CurrentUser;
import com.skillmirror.candidate.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/candidate")
@RequiredArgsConstructor
public class CandidateController {
    private final CandidateService service;

    @GetMapping("/profile")
    public CandidateProfileResponse get() {
        return service.get(CurrentUser.uid());
    }

    @PostMapping("/onboarding")
    public CandidateProfileResponse onboarding(@Valid @RequestBody OnboardingRequest r) {
        return service.onboarding(CurrentUser.uid(), r);
    }

    @PutMapping("/profile")
    public CandidateProfileResponse updateProfile(@Valid @RequestBody CandidateProfileRequest r) {
        return service.updateProfile(CurrentUser.uid(), r);
    }

    @PutMapping("/preferences")
    public CandidateProfileResponse updatePreferences(@RequestBody PreferencesRequest r) {
        return service.updatePreferences(CurrentUser.uid(), r);
    }
}