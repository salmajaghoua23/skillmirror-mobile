package com.skillmirror.account;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {}

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("Utilisateur non authentifié");
    }

    /** UID Firebase de l'utilisateur connecté */
    public static String uid() {
        return jwt().getSubject();
    }

    public static String email() {
        return jwt().getClaimAsString("email");
    }

    public static String name() {
        return jwt().getClaimAsString("name");
    }

    /** Claim "role" posé par set-role (peut être null avant) */
    public static String role() {
        return jwt().getClaimAsString("role");
    }
}