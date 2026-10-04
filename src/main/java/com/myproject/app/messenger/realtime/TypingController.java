package com.myproject.app.messenger.realtime;

import com.myproject.app.messenger.chat.ConversationMemberRepository;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** Ephemeral typing indicators: SEND to /app/conversations/{id}/typing; relayed to the other members, never stored. */
@Controller
class TypingController {

    private final ConversationMemberRepository members;
    private final ChatEvents events;

    TypingController(ConversationMemberRepository members, ChatEvents events) {
        this.members = members;
        this.events = events;
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    void typing(@DestinationVariable UUID conversationId, Principal principal) {
        var usernames = members.findUsernames(conversationId);
        if (!usernames.contains(principal.getName())) {
            return;
        }
        events.publish(usernames.stream().filter(u -> !u.equals(principal.getName())).toList(),
                new ChatEvent("typing", conversationId, Map.of("username", principal.getName())));
    }
}
