package com.nabeelsayyed.benefitsportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who can open which pages. Staff accounts live in the database (see StaffUserDetailsService)
 * and come in two roles:
 *
 * - CASEWORKER: the claims queue, claim pages, and the staff API.
 * - SUPERVISOR: all of that, plus the Staff page for managing accounts.
 *
 * Finer rules (only a supervisor can decide an appeal, and never on a claim they denied
 * themselves) live in ClaimService, so the web pages and the API both obey them.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/claims/**", "/status", "/status/**", "/css/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/claims").permitAll() // public claim submission only
                        .requestMatchers("/admin/staff/**").hasRole("SUPERVISOR")
                        .requestMatchers("/api/**").hasAnyRole("CASEWORKER", "SUPERVISOR")
                        .requestMatchers("/admin/**").hasAnyRole("CASEWORKER", "SUPERVISOR")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin/claims", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));

        return http.build();
    }
}
