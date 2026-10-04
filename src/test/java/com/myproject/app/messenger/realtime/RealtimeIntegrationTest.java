package com.myproject.app.messenger.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.myproject.app.messenger.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Real STOMP clients against the running server. */
class RealtimeIntegrationTest extends IntegrationTest {

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(
                List.of(new ByteArrayMessageConverter(), new MappingJackson2MessageConverter())));
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connect, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session) throws Exception {
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        StompSession.Receiptable subscription = session.subscribe("/user/queue/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((JsonNode) payload);
            }
        });
        assertThat(subscription).isNotNull();
        Thread.sleep(300); // let the SUBSCRIBE frame reach the broker before events are produced
        return events;
    }

    private JsonNode next(BlockingQueue<JsonNode> events, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode event = events.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (event != null && event.at("/type").asText().equals(type)) {
                return event;
            }
        }
        throw new AssertionError("No '" + type + "' event within 5s");
    }

    @Test
    void membersReceiveMessageEventsAndOutsidersDoNot() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        Session eve = signUp("eve");
        BlockingQueue<JsonNode> bobEvents = subscribe(connect(bob.token()));
        BlockingQueue<JsonNode> eveEvents = subscribe(connect(eve.token()));

        String id = body(call(ada, post("/api/conversations/direct"), Map.of("username", "bob"), 201)).at("/id").asText();
        assertThat(next(bobEvents, "conversation.created").at("/conversationId").asText()).isEqualTo(id);

        call(ada, post("/api/conversations/" + id + "/messages"), Map.of("body", "hi bob"), 201);
        JsonNode created = next(bobEvents, "message.created");
        assertThat(created.at("/data/body").asText()).isEqualTo("hi bob");
        assertThat(created.at("/data/sender/username").asText()).isEqualTo("ada");
        assertThat(created.at("/data/seq").asLong()).isEqualTo(1);

        call(bob, post("/api/conversations/" + id + "/read"), Map.of("seq", 1), 200);
        assertThat(eveEvents.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void readReceiptsAndTypingReachTheOtherMember() throws Exception {
        Session ada = signUp("ada");
        Session bob = signUp("bob");
        StompSession adaSocket = connect(ada.token());
        BlockingQueue<JsonNode> adaEvents = subscribe(adaSocket);
        BlockingQueue<JsonNode> bobEvents = subscribe(connect(bob.token()));
        String id = body(call(ada, post("/api/conversations/direct"), Map.of("username", "bob"), 201)).at("/id").asText();
        call(ada, post("/api/conversations/" + id + "/messages"), Map.of("body", "ping"), 201);

        call(bob, post("/api/conversations/" + id + "/read"), Map.of("seq", 1), 200);
        JsonNode read = next(adaEvents, "read");
        assertThat(read.at("/data/username").asText()).isEqualTo("bob");
        assertThat(read.at("/data/seq").asLong()).isEqualTo(1);

        adaSocket.send("/app/conversations/" + id + "/typing", new byte[0]);
        assertThat(next(bobEvents, "typing").at("/data/username").asText()).isEqualTo("ada");
    }

    @Test
    void connectionsWithoutAValidTokenAreRefused() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect("forged.token.value")).isInstanceOf(Exception.class);
    }

    @Test
    void usersCannotSubscribeToSharedDestinations() throws Exception {
        Session ada = signUp("ada");
        StompSession session = connect(ada.token());
        session.subscribe("/queue/events", new StompSessionHandlerAdapter() { });
        long deadline = System.currentTimeMillis() + 3000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).as("the server closes the session after a forbidden SUBSCRIBE").isFalse();
    }
}
