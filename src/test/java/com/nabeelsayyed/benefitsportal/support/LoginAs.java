package com.nabeelsayyed.benefitsportal.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.function.Supplier;

/** Runs a bit of test code as a given staff member, then logs them out again. */
public final class LoginAs {

    private LoginAs() {
    }

    public static <T> T caseworker(String username, Supplier<T> action) {
        return as(username, action, "CASEWORKER");
    }

    public static <T> T supervisor(String username, Supplier<T> action) {
        return as(username, action, "SUPERVISOR", "CASEWORKER");
    }

    public static <T> T as(String username, Supplier<T> action, String... roles) {
        var authorities = Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
