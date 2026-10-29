package com.bookstore.ecommerce.common;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller of the current request: a registered user, or anonymous (a guest). */
public record Caller(UUID userId, Set<String> roles) {

    public static final Caller GUEST = new Caller(null, Set.of());

    public static Caller from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return GUEST;
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
        return new Caller(UUID.fromString(jwt.getSubject()), roles);
    }

    public boolean isGuest() {
        return userId == null;
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    /** The user id of an authenticated caller; guests are rejected. */
    public UUID requireUser() {
        if (userId == null) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
        }
        return userId;
    }
}
