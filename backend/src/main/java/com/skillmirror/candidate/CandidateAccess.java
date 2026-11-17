package com.skillmirror.candidate;

import com.skillmirror.account.AccountRepository;
import com.skillmirror.common.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CandidateAccess {
    private final AccountRepository accountRepo;

    /** Profil de l'utilisateur connecté, à condition qu'il soit candidat. */
    public Profile candidate(String uid) {
        Profile p = accountRepo.findByFirebaseUid(uid).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil introuvable : choisir un rôle d'abord"));
        if (!"candidate".equals(p.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
        return p;
    }
}