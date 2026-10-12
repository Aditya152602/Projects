package com.devtrack.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void register_returns201_roleUser_andNeverExposesPassword() throws Exception {
        doPost("/api/auth/register", null,
                Map.of("name", "Aditya", "email", uniqueEmail("reg"), "password", "password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());       // hash must not leak
    }

    @Test
    void register_ignoresClientSuppliedAdminRole() throws Exception {
        // attacker tries privilege escalation by adding "role":"ADMIN" to the body
        doPost("/api/auth/register", null,
                Map.of("name", "Evil", "email", uniqueEmail("evil"), "password", "password123", "role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));            // extra field ignored
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail("dup");
        registerAndLogin(email);
        doPost("/api/auth/register", null,
                Map.of("name", "Again", "email", email.toUpperCase(), "password", "password123")) // case-insensitive
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidPayload_returns400_withFieldErrors() throws Exception {
        doPost("/api/auth/register", null, Map.of("name", "", "email", "not-an-email", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void login_success_returnsBearerToken() throws Exception {
        String email = uniqueEmail("login");
        registerAndLogin(email);
        doPost("/api/auth/login", null, Map.of("email", email, "password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail("wrongpw");
        registerAndLogin(email);
        doPost("/api/auth/login", null, Map.of("email", email, "password", "bad-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail_returns401_notLeakingExistence() throws Exception {
        doPost("/api/auth/login", null, Map.of("email", uniqueEmail("ghost"), "password", "password123"))
                .andExpect(status().isUnauthorized());                   // same status as wrong password
    }

    @Test
    void me_withValidToken_returnsCurrentUser() throws Exception {
        String email = uniqueEmail("me");
        String token = registerAndLogin(email);
        doGet("/api/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        doGet("/api/users/me", null).andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        doGet("/api/users/me", "garbage.token.value").andExpect(status().isUnauthorized());
    }
}
