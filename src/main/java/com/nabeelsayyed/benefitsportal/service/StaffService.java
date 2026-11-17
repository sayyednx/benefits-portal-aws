package com.nabeelsayyed.benefitsportal.service;

import com.nabeelsayyed.benefitsportal.exception.InvalidClaimActionException;
import com.nabeelsayyed.benefitsportal.model.StaffRole;
import com.nabeelsayyed.benefitsportal.model.StaffUser;
import com.nabeelsayyed.benefitsportal.repository.StaffUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Creating staff accounts and switching them on or off. Supervisors only (see SecurityConfig). */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    private final StaffUserRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentActor currentActor;
    private final Clock clock;

    public StaffService(StaffUserRepository staffRepository, PasswordEncoder passwordEncoder,
                        CurrentActor currentActor, Clock clock) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffUser> findAll() {
        return staffRepository.findAllByOrderByUsernameAsc();
    }

    @Transactional
    public StaffUser create(String username, String fullName, StaffRole role, String password) {
        String cleanUsername = username == null ? "" : username.trim().toLowerCase();
        if (!cleanUsername.matches("[a-z0-9._-]{3,50}")) {
            throw new InvalidClaimActionException(
                    "Usernames are 3 to 50 characters: lowercase letters, numbers, dots, dashes or underscores.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new InvalidClaimActionException("Enter the staff member's full name.");
        }
        if (role == null) {
            throw new InvalidClaimActionException("Pick a role.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidClaimActionException("Passwords must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (staffRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new InvalidClaimActionException("The username " + cleanUsername + " is already taken.");
        }
        StaffUser saved = staffRepository.save(new StaffUser(cleanUsername, passwordEncoder.encode(password),
                fullName.trim(), role, LocalDateTime.now(clock)));
        log.info("Staff account {} ({}) created by {}", saved.getUsername(), role, currentActor.name());
        return saved;
    }

    @Transactional
    public StaffUser setActive(Long id, boolean active) {
        StaffUser staff = staffRepository.findById(id)
                .orElseThrow(() -> new InvalidClaimActionException("No staff account with id " + id + "."));
        if (!active && staff.getUsername().equalsIgnoreCase(currentActor.name())) {
            throw new InvalidClaimActionException("You can't switch off your own account.");
        }
        staff.setActive(active);
        log.info("Staff account {} {} by {}", staff.getUsername(), active ? "switched on" : "switched off",
                currentActor.name());
        return staffRepository.save(staff);
    }
}
