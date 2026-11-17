package com.nabeelsayyed.benefitsportal.controller;

import com.nabeelsayyed.benefitsportal.repository.StaffUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Logging in with database accounts, and who can reach the Staff page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAccessTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffUserRepository staffRepository;

    @Test
    void theFirstSupervisorIsCreatedAndCanLogIn() throws Exception {
        assertThat(staffRepository.findByUsernameIgnoreCase("admin")).isPresent();

        mvc.perform(formLogin("/admin/login").user("admin").password("test-password-123"))
                .andExpect(authenticated().withRoles("SUPERVISOR", "CASEWORKER"));
    }

    @Test
    void aWrongPasswordDoesNotLogIn() throws Exception {
        mvc.perform(formLogin("/admin/login").user("admin").password("nope"))
                .andExpect(unauthenticated());
    }

    @Test
    @WithMockUser(roles = "CASEWORKER")
    void aCaseworkerCantOpenTheStaffPage() throws Exception {
        mvc.perform(get("/admin/staff")).andExpect(status().isForbidden());
    }

    /** Logged in as the supervisor for one request (so later login checks start logged out). */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor supervisor() {
        return user("admin").roles("SUPERVISOR", "CASEWORKER");
    }

    @Test
    void aSupervisorCanAddACaseworkerWhoCanThenLogIn() throws Exception {
        mvc.perform(get("/admin/staff").with(supervisor()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Staff accounts")));

        mvc.perform(post("/admin/staff").with(csrf()).with(supervisor())
                        .param("username", "Jordan.Case")
                        .param("fullName", "Jordan Case")
                        .param("role", "CASEWORKER")
                        .param("password", "long-enough-password"))
                .andExpect(redirectedUrl("/admin/staff"))
                .andExpect(flash().attribute("message", containsString("jordan.case")));

        mvc.perform(formLogin("/admin/login").user("jordan.case").password("long-enough-password"))
                .andExpect(authenticated().withRoles("CASEWORKER"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPERVISOR", "CASEWORKER"})
    void aShortPasswordIsRefused() throws Exception {
        mvc.perform(post("/admin/staff").with(csrf())
                        .param("username", "short.pw")
                        .param("fullName", "Short Pw")
                        .param("role", "CASEWORKER")
                        .param("password", "short"))
                .andExpect(flash().attribute("error", containsString("at least 12")));
    }

    @Test
    void aSwitchedOffAccountCantLogIn() throws Exception {
        mvc.perform(post("/admin/staff").with(csrf()).with(supervisor())
                .param("username", "leaving.soon")
                .param("fullName", "Leaving Soon")
                .param("role", "CASEWORKER")
                .param("password", "long-enough-password"));
        Long id = staffRepository.findByUsernameIgnoreCase("leaving.soon").orElseThrow().getId();

        mvc.perform(post("/admin/staff/" + id + "/active").with(csrf()).with(supervisor()).param("active", "false"))
                .andExpect(redirectedUrl("/admin/staff"));

        mvc.perform(formLogin("/admin/login").user("leaving.soon").password("long-enough-password"))
                .andExpect(unauthenticated());
    }
}
