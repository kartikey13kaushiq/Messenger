package com.myproject.app.messenger.realtime;

import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers events only after the transaction that produced them commits, so clients never see rolled-back state. */
@Component
public class ChatEvents {

    public static final String USER_QUEUE = "/queue/events";

    private final ApplicationEventPublisher publisher;
    private final SimpMessagingTemplate template;

    public ChatEvents(ApplicationEventPublisher publisher, SimpMessagingTemplate template) {
        this.publisher = publisher;
        this.template = template;
    }

    public void publish(Collection<String> usernames, ChatEvent event) {
        publisher.publishEvent(new Outbound(List.copyOf(usernames), event));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void deliver(Outbound outbound) {
        outbound.usernames().forEach(username -> template.convertAndSendToUser(username, USER_QUEUE, outbound.event()));
    }

    record Outbound(List<String> usernames, ChatEvent event) {
    }
}
