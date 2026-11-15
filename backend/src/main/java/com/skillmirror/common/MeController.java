package com.skillmirror.common;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MeController {

    private final ProfileRepository profiles;

    /** Public : sert à vérifier que le serveur tourne. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /** Protégé : renvoie le compte lié au token Firebase. */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication auth) {
        String uid = (String) auth.getPrincipal();
        return profiles.findByFirebaseUid(uid)
                .map(p -> ResponseEntity.ok(new MeResponse(
                        p.getId(), p.getFullName(), p.getEmail(), p.getRole(),
                        Boolean.TRUE.equals(p.getOnboardingDone()))))
                .orElse(ResponseEntity.notFound().build());
    }
}
