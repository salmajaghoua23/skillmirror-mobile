package com.skillmirror.account;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.skillmirror.common.Profile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegistrationController {
    private final AccountRepository repo;

    public record RoleRequest(
            @Pattern(regexp = "candidate|recruiter", message = "role doit être candidate ou recruiter")
            String role) {}

    /** Crée la ligne profiles au choix du rôle (idempotent, rôle non modifiable). */
    @PostMapping("/register")
    @Transactional
    public Map<String, Object> register(@Valid @RequestBody RoleRequest body) {
        String uid = CurrentUser.uid();
        Optional<Profile> existing = repo.findByFirebaseUid(uid);
        Profile p;
        if (existing.isPresent()) {
            p = existing.get();
            if (!body.role().equals(p.getRole())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Rôle déjà défini : " + p.getRole());
            }
        } else {
            UserRecord u;
            try {
                u = FirebaseAuth.getInstance().getUser(uid);
            } catch (FirebaseAuthException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Compte Firebase illisible");
            }
            String email = u.getEmail() != null ? u.getEmail() : "";
            String name = u.getDisplayName();
            if (name == null || name.isBlank()) {
                name = email.contains("@") ? email.substring(0, email.indexOf('@')) : "Utilisateur";
            }
            Profile n = new Profile();
            n.setId(UUID.randomUUID());
            n.setFirebaseUid(uid);
            n.setEmail(email);
            n.setFullName(name);
            n.setRole(body.role());
            n.setOnboardingDone(false);
            p = repo.save(n);
        }
        return Map.of(
                "role", p.getRole(),
                "onboardingDone", Boolean.TRUE.equals(p.getOnboardingDone()),
                "fullName", p.getFullName());
    }
}