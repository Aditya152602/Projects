package com.devtrack.integration;

import com.devtrack.entity.Role;
import com.devtrack.entity.User;
import com.devtrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTest extends IntegrationTestBase {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // Public signup can never create an ADMIN, so insert one straight into the DB, then log in normally
    private String adminToken() throws Exception {
        String email = uniqueEmail("admin");
        userRepository.save(User.builder().name("Admin").email(email)
                .password(passwordEncoder.encode("password123")).role(Role.ADMIN).build());
        return body(doPost("/api/auth/login", null, Map.of("email", email, "password", "password123"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    @Test
    void adminEndpoint_anonymous_401() throws Exception {
        doGet("/api/admin/users", null).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_normalUser_403() throws Exception {
        String token = registerAndLogin(uniqueEmail("user"));
        doGet("/api/admin/users", token).andExpect(status().isForbidden());   // role check, not just login
    }

    @Test
    void adminEndpoint_admin_200_andPasswordsNeverListed() throws Exception {
        registerAndLogin(uniqueEmail("someone"));
        doGet("/api/admin/users", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void admin_canViewAndDeleteAnyProject() throws Exception {
        String owner = registerAndLogin(uniqueEmail("owner"));
        long pid = createProject(owner, "NotAdminsProject");
        String admin = adminToken();

        doGet("/api/projects/" + pid, admin).andExpect(status().isOk());
        doDelete("/api/projects/" + pid, admin).andExpect(status().isNoContent());
        doGet("/api/projects/" + pid, owner).andExpect(status().isNotFound());
    }
}
