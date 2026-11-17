package com.nabeelsayyed.benefitsportal.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Works out who is doing the current action, for the audit history. */
@Component
public class CurrentActor {

    public static final String CLAIMANT = "claimant";

    /** The logged-in staff username, or "claimant" when nobody is logged in (a public page). */
    public String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return CLAIMANT;
        }
        return auth.getName();
    }

    /** Whether the current user has a role, e.g. hasRole("SUPERVISOR"). */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
