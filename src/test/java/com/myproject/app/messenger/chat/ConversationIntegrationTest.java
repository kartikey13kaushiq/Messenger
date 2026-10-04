package com.myproject.app.messenger.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.myproject.app.messenger.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ConversationIntegrationTest extends IntegrationTest {

    @Test
    void directConversationsAreUniquePerPair() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        JsonNode created = body(call(ada, post("/api/conversations/direct"), Map.of("username", "BOB"), 201));
        String id = created.at("/id").asText();
        assertThat(created.at("/type").asText()).isEqualTo("DIRECT");
        assertThat(created.at("/title").asText()).isEqualTo("User bob");
        assertThat(created.at("/members").size()).isEqualTo(2);

        JsonNode fromBob = body(call(bob, post("/api/conversations/direct"), Map.of("username", "ada"), 200));
        assertThat(fromBob.at("/id").asText()).isEqualTo(id);
        assertThat(fromBob.at("/title").asText()).isEqualTo("User ada");

        call(ada, post("/api/conversations/direct"), Map.of("username", "ada"), 422);
        call(ada, post("/api/conversations/direct"), Map.of("username", "ghost"), 404);
        call(ada, delete("/api/conversations/" + id + "/members/me"), null, 422);
        call(ada, patch("/api/conversations/" + id), Map.of("title", "x"), 422);
    }

    @Test
    void concurrentOpensOfTheSamePairCreateOneConversation() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        List<Callable<String>> opens = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Session caller = i % 2 == 0 ? ada : bob;
            String other = i % 2 == 0 ? "bob" : "ada";
            opens.add(() -> {
                var result = mvc.perform(post("/api/conversations/direct")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("username", other)))).andReturn();
                assertThat(result.getResponse().getStatus()).isIn(200, 201);
                return body(result).at("/id").asText();
            });
        }
        Set<String> ids = new HashSet<>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (Future<String> f : pool.invokeAll(opens)) {
                ids.add(f.get());
            }
        }
        assertThat(ids).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from conversation_members", Integer.class)).isEqualTo(2);
    }

    @Test
    void groupLifecycle() throws Exception {
        Session owner = signUp("owner");
        Session amy = signUp("amy");
        Session ben = signUp("ben");
        Session cat = signUp("cat");

        JsonNode unknown = body(call(owner, post("/api/conversations/groups"),
                Map.of("title", "Study group", "members", List.of("amy", "zed", "yan")), 404));
        assertThat(unknown.at("/unknownUsernames").toString()).contains("zed", "yan");
        call(owner, post("/api/conversations/groups"), Map.of("title", "Solo", "members", List.of("owner")), 422);

        JsonNode group = body(call(owner, post("/api/conversations/groups"),
                Map.of("title", "Study group", "members", List.of("amy", "AMY", "ben")), 201));
        String id = group.at("/id").asText();
        assertThat(group.at("/members").size()).isEqualTo(3);
        assertThat(group.at("/members/0/role").asText()).isEqualTo("OWNER");

        call(amy, post("/api/conversations/" + id + "/members"), Map.of("username", "cat"), 403);
        call(owner, post("/api/conversations/" + id + "/members"), Map.of("username", "cat"), 200);
        call(owner, post("/api/conversations/" + id + "/members"), Map.of("username", "cat"), 409);
        JsonNode renamed = body(call(owner, patch("/api/conversations/" + id), Map.of("title", "Exam prep"), 200));
        assertThat(renamed.at("/title").asText()).isEqualTo("Exam prep");
        assertThat(body(call(cat, get("/api/conversations/" + id), null, 200)).at("/members").size()).isEqualTo(4);

        // The owner leaves: the longest-standing member takes over.
        call(owner, delete("/api/conversations/" + id + "/members/me"), null, 204);
        call(owner, get("/api/conversations/" + id), null, 404);
        JsonNode after = body(call(amy, get("/api/conversations/" + id), null, 200));
        assertThat(after.at("/members").findValuesAsText("role")).containsOnlyOnce("OWNER");

        for (Session s : List.of(amy, ben, cat)) {
            call(s, delete("/api/conversations/" + id + "/members/me"), null, 204);
        }
        assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isZero();
    }

    @Test
    void newMembersStartWithNothingUnread() throws Exception {
        Session owner = signUp("owner");
        signUp("amy");
        Session late = signUp("late");
        String id = body(call(owner, post("/api/conversations/groups"),
                Map.of("title", "G", "members", List.of("amy")), 201)).at("/id").asText();
        call(owner, post("/api/conversations/" + id + "/messages"), Map.of("body", "before you joined"), 201);
        call(owner, post("/api/conversations/" + id + "/members"), Map.of("username", "late"), 200);
        JsonNode seen = body(call(late, get("/api/conversations/" + id), null, 200));
        assertThat(seen.at("/unreadCount").asLong()).isZero();
        assertThat(body(call(late, get("/api/conversations/" + id + "/messages"), null, 200)).at("/messages").size())
                .isEqualTo(1);
    }

    @Test
    void outsidersCannotSeeAConversation() throws Exception {
        Session ada = signUp("ada");
        signUp("bob");
        Session eve = signUp("eve");
        String id = body(call(ada, post("/api/conversations/direct"), Map.of("username", "bob"), 201)).at("/id").asText();
        call(eve, get("/api/conversations/" + id), null, 404);
        call(eve, get("/api/conversations/" + id + "/messages"), null, 404);
        call(eve, post("/api/conversations/" + id + "/messages"), Map.of("body", "hi"), 404);
        call(eve, post("/api/conversations/" + id + "/read"), Map.of("seq", 1), 404);
        assertThat(body(call(eve, get("/api/conversations"), null, 200)).size()).isZero();
        call(ada, get("/api/conversations/not-a-uuid"), null, 400);
    }

    @Test
    void listIsOrderedByActivityWithPreviewsAndUnreadCounts() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        signUp("cyd");
        String withBob = body(call(ada, post("/api/conversations/direct"), Map.of("username", "bob"), 201)).at("/id").asText();
        String withCy = body(call(ada, post("/api/conversations/direct"), Map.of("username", "cyd"), 201)).at("/id").asText();

        call(bob, post("/api/conversations/" + withBob + "/messages"), Map.of("body", "first"), 201);
        clock.advance(Duration.ofSeconds(5));
        call(ada, post("/api/conversations/" + withCy + "/messages"), Map.of("body", "hello cyd"), 201);
        clock.advance(Duration.ofSeconds(5));
        call(bob, post("/api/conversations/" + withBob + "/messages"), Map.of("body", "second"), 201);

        JsonNode list = body(call(ada, get("/api/conversations"), null, 200));
        assertThat(list.size()).isEqualTo(2);
        assertThat(List.of(list.at("/0/id").asText(), list.at("/1/id").asText())).containsExactly(withBob, withCy);
        assertThat(list.at("/0/unreadCount").asLong()).isEqualTo(2);
        assertThat(list.at("/0/lastMessage/body").asText()).isEqualTo("second");
        assertThat(list.at("/1/unreadCount").asLong()).isZero(); // your own messages count as read

        JsonNode read = body(call(ada, post("/api/conversations/" + withBob + "/read"), Map.of("seq", 1), 200));
        assertThat(read.at("/unreadCount").asLong()).isEqualTo(1);
        call(ada, post("/api/conversations/" + withBob + "/read"), Map.of("seq", 99), 200); // clamped to the latest
        call(ada, post("/api/conversations/" + withBob + "/read"), Map.of("seq", 0), 200);  // never moves back
        assertThat(body(call(ada, get("/api/conversations/" + withBob), null, 200)).at("/unreadCount").asLong()).isZero();
    }
}
