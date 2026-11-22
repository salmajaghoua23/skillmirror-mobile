package com.skillmirror.recruiter.offer;

import com.skillmirror.recruiter.offer.OfferDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class RecruiterOfferController {

    private final RecruiterOfferService service;

    // --- Offres ---
    @GetMapping("/offers")
    public List<OfferResponse> list(Authentication auth,
                                    @RequestParam(required = false) String status) {
        return service.list(auth.getName(), status);
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse create(Authentication auth, @Valid @RequestBody OfferRequest req) {
        return service.create(auth.getName(), req);
    }

    @GetMapping("/offers/{id}")
    public OfferResponse get(Authentication auth, @PathVariable UUID id) {
        return service.get(auth.getName(), id);
    }

    @PutMapping("/offers/{id}")
    public OfferResponse update(Authentication auth, @PathVariable UUID id,
                                @Valid @RequestBody OfferRequest req) {
        return service.update(auth.getName(), id, req);
    }

    @PutMapping("/offers/{id}/status")
    public OfferResponse status(Authentication auth, @PathVariable UUID id,
                                @Valid @RequestBody OfferStatusRequest req) {
        return service.setStatus(auth.getName(), id, req.status());
    }

    @DeleteMapping("/offers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        service.delete(auth.getName(), id);
    }

    // --- Candidatures ---
    @GetMapping("/offers/{id}/applications")
    public List<ApplicationResponse> applications(Authentication auth, @PathVariable UUID id) {
        return service.applicationsOf(auth.getName(), id);
    }

    @PutMapping("/applications/{id}/status")
    public ApplicationResponse updateApplication(Authentication auth, @PathVariable UUID id,
                                                 @Valid @RequestBody ApplicationStatusRequest req) {
        return service.updateApplicationStatus(auth.getName(), id, req.status());
    }

    // --- Candidats ---
    @GetMapping("/offers/{id}/matches")
    public List<CandidateSummary> matches(Authentication auth, @PathVariable UUID id) {
        return service.matches(auth.getName(), id);
    }

    @GetMapping("/candidates/{profileId}")
    public CandidateDetail candidate(Authentication auth, @PathVariable UUID profileId) {
        return service.candidateDetail(auth.getName(), profileId);
    }

    // --- Dashboard ---
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(Authentication auth) {
        return service.dashboard(auth.getName());
    }
}
