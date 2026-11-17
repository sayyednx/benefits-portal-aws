package com.nabeelsayyed.benefitsportal.config;

import com.nabeelsayyed.benefitsportal.model.StaffRole;
import com.nabeelsayyed.benefitsportal.model.StaffUser;
import com.nabeelsayyed.benefitsportal.repository.StaffUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The first-supervisor setup that runs when the app starts with an empty staff table. */
class StaffBootstrapTest {

    private final StaffUserRepository repository = mock(StaffUserRepository.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private StaffBootstrap bootstrapWithPassword(String password) {
        StaffBootstrap bootstrap = new StaffBootstrap(repository, encoder, Clock.systemDefaultZone());
        ReflectionTestUtils.setField(bootstrap, "adminUsername", "admin");
        ReflectionTestUtils.setField(bootstrap, "adminPassword", password);
        return bootstrap;
    }

    @Test
    void refusesToStartWithNoStaffAndNoAdminPassword() {
        when(repository.count()).thenReturn(0L);

        assertThatThrownBy(() -> bootstrapWithPassword("").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD is not set");
    }

    @Test
    void createsTheFirstSupervisorWithAHashedPassword() {
        when(repository.count()).thenReturn(0L);

        bootstrapWithPassword("a-real-password").run(null);

        verify(repository).save(argThat((StaffUser user) ->
                user.getUsername().equals("admin")
                        && user.getRole() == StaffRole.SUPERVISOR
                        && !user.getPasswordHash().contains("a-real-password")
                        && encoder.matches("a-real-password", user.getPasswordHash())));
    }

    @Test
    void doesNothingOnceStaffAccountsExist() {
        when(repository.count()).thenReturn(3L);

        bootstrapWithPassword("").run(null);

        verify(repository, never()).save(any());
    }
}
