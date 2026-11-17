package com.nabeelsayyed.benefitsportal.security;

import com.nabeelsayyed.benefitsportal.model.StaffRole;
import com.nabeelsayyed.benefitsportal.model.StaffUser;
import com.nabeelsayyed.benefitsportal.repository.StaffUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Tells Spring Security how to load a staff member at login: from the staff_users table. */
@Service
public class StaffUserDetailsService implements UserDetailsService {

    public static final String CASEWORKER = "ROLE_CASEWORKER";
    public static final String SUPERVISOR = "ROLE_SUPERVISOR";

    private final StaffUserRepository staffRepository;

    public StaffUserDetailsService(StaffUserRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        StaffUser staff = staffRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No staff account: " + username));

        // A supervisor can do everything a caseworker can, so they get both roles.
        List<SimpleGrantedAuthority> authorities = staff.getRole() == StaffRole.SUPERVISOR
                ? List.of(new SimpleGrantedAuthority(SUPERVISOR), new SimpleGrantedAuthority(CASEWORKER))
                : List.of(new SimpleGrantedAuthority(CASEWORKER));

        return User.withUsername(staff.getUsername())
                .password(staff.getPasswordHash())
                .authorities(authorities)
                .disabled(!staff.isActive())
                .build();
    }
}
