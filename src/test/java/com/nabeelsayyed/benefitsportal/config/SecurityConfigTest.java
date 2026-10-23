package com.nabeelsayyed.benefitsportal.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private SecurityConfig configWithPassword(String password) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "adminUsername", "admin");
        ReflectionTestUtils.setField(config, "adminPassword", password);
        return config;
    }

    @Test
    void refusesToStartWithoutAnAdminPassword() {
        SecurityConfig config = configWithPassword("");
        PasswordEncoder encoder = config.passwordEncoder();

        assertThatThrownBy(() -> config.userDetailsService(encoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD is not set");
    }

    @Test
    void createsTheAdminUserWhenAPasswordIsSet() {
        SecurityConfig config = configWithPassword("a-real-password");
        PasswordEncoder encoder = config.passwordEncoder();

        var users = config.userDetailsService(encoder);

        assertThat(encoder.matches("a-real-password", users.loadUserByUsername("admin").getPassword())).isTrue();
    }
}
