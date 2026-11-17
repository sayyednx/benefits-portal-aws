package com.nabeelsayyed.benefitsportal.config;

import com.nabeelsayyed.benefitsportal.model.StaffRole;
import com.nabeelsayyed.benefitsportal.model.StaffUser;
import com.nabeelsayyed.benefitsportal.repository.StaffUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Solves the "first login" problem: on a brand-new database there are no staff accounts,
 * so nobody could log in to create one. On startup, if the staff table is empty, this creates
 * one supervisor from ADMIN_USERNAME / ADMIN_PASSWORD. That supervisor then creates
 * everyone else from the Staff page.
 *
 * There is still no built-in default password: with an empty staff table and no
 * ADMIN_PASSWORD set, the app refuses to start.
 */
@Component
public class StaffBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final StaffUserRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    public StaffBootstrap(StaffUserRepository staffRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (staffRepository.count() > 0) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD is not set. Set it as an environment variable (or in .env for docker compose) "
                            + "so the first supervisor account can be created.");
        }
        staffRepository.save(new StaffUser(adminUsername.toLowerCase(), passwordEncoder.encode(adminPassword),
                "First supervisor", StaffRole.SUPERVISOR, LocalDateTime.now(clock)));
        log.info("Created the first supervisor account '{}'", adminUsername);
    }
}
