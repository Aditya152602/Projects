package com.devtrack.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the FULL app (security filter, controllers, services, JPA) against a REAL MySQL 8.4
 * running in a Docker container (Testcontainers). Docker must be running to execute these tests.
 * No @Transactional on tests on purpose: each request commits for real, so lazy-loading
 * and transaction-boundary bugs show up. Isolation = unique emails per test.
 */
@SpringBootTest                 // start full Spring context
@AutoConfigureMockMvc           // gives us MockMvc wired through the real security filter chain
@ActiveProfiles("test")         // loads application-test.yml
public abstract class IntegrationTestBase {

    // ONE container shared by every test class (singleton pattern): starting MySQL takes seconds,
    // so we start it once per JVM. Same image version as docker-compose.yml -> test env matches prod.
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("devtrack_test");

    static {
        MYSQL.start();              // runs when the first test class loads; Ryuk sidecar removes it when JVM exits
    }

    // Point Spring's datasource at the container. Random host port, so values are read at runtime.
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.com";   // never collides across tests
    }

    protected String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    // Adds "Authorization: Bearer <token>" only when token != null (null = anonymous call)
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        if (token != null) b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return b;
    }

    protected ResultActions doGet(String url, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(url), token));
    }

    protected ResultActions doPost(String url, String token, Object body) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)), token));
    }

    protected ResultActions doPut(String url, String token, Object body) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)), token));
    }

    protected ResultActions doDelete(String url, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.delete(url), token));
    }

    // Parse response body JSON so tests can read ids/tokens out of it
    protected JsonNode body(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    protected String registerAndLogin(String email) throws Exception {
        doPost("/api/auth/register", null,
                Map.of("name", "Test User", "email", email, "password", "password123"))
                .andExpect(status().isCreated());
        return body(doPost("/api/auth/login", null, Map.of("email", email, "password", "password123"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    protected long meId(String token) throws Exception {
        return body(doGet("/api/users/me", token).andExpect(status().isOk())).get("id").asLong();
    }

    protected long createProject(String token, String name) throws Exception {
        return body(doPost("/api/projects", token, Map.of("name", name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected void addMember(String ownerToken, long projectId, String memberEmail) throws Exception {
        doPost("/api/projects/" + projectId + "/members", ownerToken, Map.of("email", memberEmail))
                .andExpect(status().isOk());
    }

    // Builds a task request body; null args are simply left out
    protected Map<String, Object> taskBody(String title, String status, String priority, Long assigneeId) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        if (status != null) m.put("status", status);
        if (priority != null) m.put("priority", priority);
        if (assigneeId != null) m.put("assigneeId", assigneeId);
        return m;
    }

    protected long createTask(String token, long projectId, Map<String, Object> body) throws Exception {
        return body(doPost("/api/projects/" + projectId + "/tasks", token, body)
                .andExpect(status().isCreated())).get("id").asLong();
    }

}
