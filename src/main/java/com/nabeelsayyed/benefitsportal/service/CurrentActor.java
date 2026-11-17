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
}
