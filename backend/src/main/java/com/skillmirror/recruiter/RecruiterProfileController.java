package com.skillmirror.recruiter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class RecruiterProfileController {

    private final RecruiterProfileService service;

    @GetMapping("/profile")
    public RecruiterProfileResponse get(Authentication auth) {
        return service.get(auth.getName());
    }

    @PutMapping("/profile")
    public RecruiterProfileResponse save(Authentication auth,
                                         @Valid @RequestBody RecruiterProfileRequest req) {
        return service.save(auth.getName(), req);
    }
}