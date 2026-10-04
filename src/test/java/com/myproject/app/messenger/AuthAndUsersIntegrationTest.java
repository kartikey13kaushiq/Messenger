package com.myproject.app.messenger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.myproject.app.messenger.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AuthAndUsersIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginAndProfile() throws Exception {
        Session ada = signUp("ada");
        assertThat(jdbc.queryForObject("select password_hash from users", String.class)).startsWith("$2a$");

        JsonNode me = body(call(ada, get("/api/users/me"), null, 200));
        assertThat(me.at("/username").asText()).isEqualTo("ada");
        assertThat(me.has("passwordHash")).isFalse();

        JsonNode updated = body(call(ada, patch("/api/users/me"), Map.of("displayName", "Ada Lovelace"), 200));
        assertThat(updated.at("/displayName").asText()).isEqualTo("Ada Lovelace");
    }

    @Test
    void usernamesAreUniqueRegardlessOfCase() throws Exception {
        signUp("grace");
        JsonNode taken = body(perform(post("/api/auth/register"),
                Map.of("username", "GRACE", "displayName", "G", "password", "another-password"), 409));
        assertThat(taken.at("/code").asText()).isEqualTo("USERNAME_TAKEN");
    }

    @Test
    void registrationIsValidated() throws Exception {
        JsonNode bad = body(perform(post("/api/auth/register"),
                Map.of("username", "no spaces!", "displayName", "", "password", "short"), 400));
        assertThat(bad.at("/code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(bad.at("/fields").fieldNames()).toIterable().containsExactlyInAnyOrder("username", "displayName", "password");
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        signUp("linus");
        JsonNode wrongPassword = body(perform(post("/api/auth/login"), Map.of("username", "linus", "password", "nope-nope-nope"), 401));
        JsonNode unknownUser = body(perform(post("/api/auth/login"), Map.of("username", "nobody", "password", "nope-nope-nope"), 401));
        assertThat(wrongPassword.at("/detail")).isEqualTo(unknownUser.at("/detail"));
        perform(post("/api/auth/login"), Map.of("username", "LINUS", "password", "correct-horse-battery"), 200);
    }

    @Test
    void protectedEndpointsNeedAValidUnexpiredToken() throws Exception {
        Session s = signUp("tim");
        JsonNode none = body(perform(get("/api/users/me"), null, 401));
        assertThat(none.at("/code").asText()).isEqualTo("UNAUTHORIZED");
        perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"), null, 401);
        perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + s.token().replaceFirst(".$", "x")), null, 401);
        clock.advance(Duration.ofHours(13));
        perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + s.token()), null, 401);
    }

    @Test
    void userSearchMatchesPrefixOrDisplayNameAndExcludesTheCaller() throws Exception {
        Session me = signUp("margaret");
        signUp("marie");
        signUp("mark");
        signUp("alan");
        JsonNode found = body(call(me, get("/api/users").param("q", "mar"), null, 200));
        assertThat(found.findValuesAsText("username")).containsExactly("marie", "mark");
        assertThat(body(call(me, get("/api/users").param("q", "User al"), null, 200)).findValuesAsText("username"))
                .containsExactly("alan");
        assertThat(body(call(me, get("/api/users").param("q", "%"), null, 200)).size()).isZero();
    }

    @Test
    void apiDocsArePublic() throws Exception {
        JsonNode docs = body(perform(get("/v3/api-docs"), null, 200));
        assertThat(docs.at("/paths").has("/api/conversations/{conversationId}/messages")).isTrue();
        perform(get("/actuator/health"), null, 200);
    }
}
