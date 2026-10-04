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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class MessageIntegrationTest extends IntegrationTest {

    String direct(Session a, String other) throws Exception {
        return body(call(a, post("/api/conversations/direct"), Map.of("username", other), 201)).at("/id").asText();
    }

    @Test
    void cursorPaginationReturnsOldestFirstPages() throws Exception {
        Session ada = signUp("ada");
        signUp("bob");
        String id = direct(ada, "bob");
        for (int i = 1; i <= 7; i++) {
            call(ada, post("/api/conversations/" + id + "/messages"), Map.of("body", "m" + i), 201);
        }
        String url = "/api/conversations/" + id + "/messages";
        JsonNode newest = body(call(ada, get(url).param("limit", "3"), null, 200));
        assertThat(newest.at("/messages").findValuesAsText("body")).containsExactly("m5", "m6", "m7");
        assertThat(newest.at("/nextBefore").asLong()).isEqualTo(5);

        JsonNode older = body(call(ada, get(url).param("limit", "3").param("before", "5"), null, 200));
        assertThat(older.at("/messages").findValuesAsText("body")).containsExactly("m2", "m3", "m4");
        JsonNode oldest = body(call(ada, get(url).param("limit", "3").param("before", "2"), null, 200));
        assertThat(oldest.at("/messages").findValuesAsText("body")).containsExactly("m1");
        assertThat(oldest.at("/nextBefore").isNull()).isTrue();

        assertThat(body(call(ada, get(url).param("limit", "1000"), null, 200)).at("/messages").size()).isEqualTo(7);
    }

    @Test
    void retriesWithTheSameClientIdAreStoredOnce() throws Exception {
        Session ada = signUp("ada");
        signUp("bob");
        String id = direct(ada, "bob");
        var request = Map.of("body", "only once", "clientMessageId", "c-123");
        JsonNode first = body(call(ada, post("/api/conversations/" + id + "/messages"), request, 201));
        JsonNode retry = body(call(ada, post("/api/conversations/" + id + "/messages"), request, 200));
        assertThat(retry.at("/id")).isEqualTo(first.at("/id"));
        assertThat(jdbc.queryForObject("select count(*) from messages", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentSendersGetGapFreeSequenceNumbers() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        String id = direct(ada, "bob");
        List<Callable<Long>> sends = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Session s = i % 2 == 0 ? ada : bob;
            String text = "msg " + i;
            sends.add(() -> body(call(s, post("/api/conversations/" + id + "/messages"), Map.of("body", text), 201))
                    .at("/seq").asLong());
        }
        List<Long> seqs = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(10)) {
            for (var f : pool.invokeAll(sends)) {
                seqs.add(f.get());
            }
        }
        assertThat(seqs).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(body(call(ada, get("/api/conversations/" + id), null, 200)).at("/lastSeq").asLong()).isEqualTo(20);
    }

    @Test
    void onlyTheSenderEditsWithinTheWindowAndDeletesAreSoft() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        String id = direct(ada, "bob");
        String base = "/api/conversations/" + id + "/messages/";
        String msg = body(call(ada, post("/api/conversations/" + id + "/messages"), Map.of("body", "  helo  "), 201))
                .at("/id").asText();

        JsonNode edited = body(call(ada, patch(base + msg), Map.of("body", "hello"), 200));
        assertThat(edited.at("/body").asText()).isEqualTo("hello");
        assertThat(edited.at("/editedAt").isNull()).isFalse();
        call(bob, patch(base + msg), Map.of("body", "hijack"), 403);
        call(bob, delete(base + msg), null, 403);
        call(ada, patch(base + UUID.randomUUID()), Map.of("body", "x"), 404);

        clock.advance(Duration.ofMinutes(16));
        JsonNode late = body(call(ada, patch(base + msg), Map.of("body", "too late"), 422));
        assertThat(late.at("/code").asText()).isEqualTo("EDIT_WINDOW_EXPIRED");

        call(ada, delete(base + msg), null, 204);
        call(ada, delete(base + msg), null, 204); // idempotent
        call(ada, patch(base + msg), Map.of("body", "resurrect"), 409);
        JsonNode page = body(call(bob, get("/api/conversations/" + id + "/messages"), null, 200));
        assertThat(page.at("/messages/0/deleted").asBoolean()).isTrue();
        assertThat(page.at("/messages/0/body").isNull()).isTrue();
    }

    @Test
    void messagesAreValidated() throws Exception {
        Session ada = signUp("ada");
        signUp("bob");
        String id = direct(ada, "bob");
        call(ada, post("/api/conversations/" + id + "/messages"), Map.of("body", "   "), 400);
        call(ada, post("/api/conversations/" + id + "/messages"), Map.of("body", "x".repeat(4001)), 400);
    }
}
