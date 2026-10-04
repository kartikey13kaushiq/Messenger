package com.myproject.app.messenger.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the whole service on a random port against PostgreSQL: Testcontainers by default, or an
 * existing database when TEST_DATABASE_URL is set. Tables are emptied before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    private static final String EXTERNAL_DB = System.getenv("TEST_DATABASE_URL");
    private static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_DB == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
        }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @LocalServerPort protected int port;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate messages, conversation_members, conversations, users cascade");
        clock.reset();
    }

    protected record Session(UUID id, String username, String token) {
    }

    protected Session signUp(String username) throws Exception {
        perform(post("/api/auth/register"), Map.of("username", username, "displayName", "User " + username,
                "password", "correct-horse-battery"), 201);
        JsonNode login = body(perform(post("/api/auth/login"),
                Map.of("username", username, "password", "correct-horse-battery"), 200));
        return new Session(UUID.fromString(login.at("/user/id").asText()), username, login.at("/accessToken").asText());
    }

    protected MvcResult perform(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        if (result.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError("Expected HTTP " + expectedStatus + " but got " + result.getResponse().getStatus()
                    + ": " + result.getResponse().getContentAsString());
        }
        return result;
    }

    protected MvcResult call(Session s, MockHttpServletRequestBuilder request, Object body, int expectedStatus)
            throws Exception {
        return perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + s.token()), body, expectedStatus);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
