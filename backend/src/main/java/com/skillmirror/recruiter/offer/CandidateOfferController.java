package com.skillmirror.recruiter.offer;

import com.skillmirror.recruiter.offer.OfferDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CandidateOfferController {

    private final CandidateOfferService service;

    /** Offres publiées (vue candidat). */
    @GetMapping("/api/offers")
    public List<OfferResponse> published(Authentication auth) {
        return service.published(auth.getName());
    }

    /** Postuler à une offre. */
    @PostMapping("/api/offers/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(Authentication auth, @PathVariable UUID id,
                                     @Valid @RequestBody(required = false) ApplyRequest req) {
        return service.apply(auth.getName(), id, req == null ? null : req.message());
    }

    /** Consentement : autoriser les recruteurs à voir mon profil. */
    @GetMapping("/api/candidate/consent")
    public Map<String, Boolean> consent(Authentication auth) {
        return Map.of("share", service.consent(auth.getName()));
    }

    @PutMapping("/api/candidate/consent")
    public Map<String, Boolean> setConsent(Authentication auth, @RequestBody ConsentRequest req) {
        return Map.of("share", service.setConsent(auth.getName(), Boolean.TRUE.equals(req.share())));
    }
}
