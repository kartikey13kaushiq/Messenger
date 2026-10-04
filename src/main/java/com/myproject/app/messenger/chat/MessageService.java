package com.myproject.app.messenger.chat;

import com.myproject.app.messenger.chat.ChatDtos.MessagePage;
import com.myproject.app.messenger.chat.ChatDtos.MessageView;
import com.myproject.app.messenger.chat.ChatDtos.SendMessageRequest;
import com.myproject.app.messenger.error.ApiException;
import com.myproject.app.messenger.realtime.ChatEvent;
import com.myproject.app.messenger.realtime.ChatEvents;
import com.myproject.app.messenger.user.AuthUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    public static final int DEFAULT_PAGE = 50;
    public static final int MAX_PAGE = 100;

    private final ConversationService conversationService;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final ChatEvents events;
    private final Clock clock;
    private final Duration editWindow;

    public MessageService(ConversationService conversationService, ConversationRepository conversations,
            ConversationMemberRepository members, MessageRepository messages, ChatEvents events, Clock clock,
            @Value("${app.messages.edit-window:PT15M}") Duration editWindow) {
        this.conversationService = conversationService;
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.events = events;
        this.clock = clock;
        this.editWindow = editWindow;
    }

    public record Sent(MessageView message, boolean created) {
    }

    @Transactional
    public Sent send(AuthUser me, UUID conversationId, SendMessageRequest request) {
        ConversationMember membership = conversationService.requireMembership(me.id(), conversationId);
        // The row lock orders concurrent senders; the idempotency check runs under it so retries cannot race.
        Conversation conversation = conversations.findForUpdate(conversationId).orElseThrow();
        String clientId = request.clientMessageId() == null || request.clientMessageId().isBlank()
                ? null : request.clientMessageId().trim();
        if (clientId != null) {
            Optional<Message> existing = messages.findByConversationIdAndSenderIdAndClientMessageId(conversationId,
                    me.id(), clientId);
            if (existing.isPresent()) {
                return new Sent(MessageView.of(existing.get()), false);
            }
        }
        Instant now = clock.instant();
        long seq = conversation.nextSeq(now);
        Message message = messages.save(new Message(conversation, membership.getUser(), seq, request.body().strip(),
                clientId, now));
        membership.markReadUpTo(seq);
        MessageView view = MessageView.of(message);
        events.publish(members.findUsernames(conversationId), new ChatEvent("message.created", conversationId, view));
        return new Sent(view, true);
    }

    @Transactional(readOnly = true)
    public MessagePage page(UUID userId, UUID conversationId, Long before, Integer limit) {
        conversationService.requireMembership(userId, conversationId);
        int size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, MAX_PAGE);
        long beforeSeq = before == null ? Long.MAX_VALUE : before;
        List<Message> newestFirst = messages.findPage(conversationId, beforeSeq, PageRequest.of(0, size + 1));
        boolean more = newestFirst.size() > size;
        List<MessageView> page = new ArrayList<>(newestFirst.subList(0, Math.min(size, newestFirst.size())).stream()
                .map(MessageView::of).toList());
        Collections.reverse(page);
        return new MessagePage(page, more ? page.getFirst().seq() : null);
    }

    @Transactional
    public MessageView edit(AuthUser me, UUID conversationId, UUID messageId, String body) {
        Message message = ownMessage(me, conversationId, messageId);
        if (message.isDeleted()) {
            throw ApiException.conflict("MESSAGE_DELETED", "Deleted messages cannot be edited");
        }
        Instant now = clock.instant();
        if (now.isAfter(message.getCreatedAt().plus(editWindow))) {
            throw ApiException.unprocessable("EDIT_WINDOW_EXPIRED",
                    "Messages can only be edited within " + editWindow.toMinutes() + " minutes of sending");
        }
        message.edit(body.strip(), now);
        MessageView view = MessageView.of(message);
        events.publish(members.findUsernames(conversationId), new ChatEvent("message.updated", conversationId, view));
        return view;
    }

    @Transactional
    public void delete(AuthUser me, UUID conversationId, UUID messageId) {
        Message message = ownMessage(me, conversationId, messageId);
        if (message.isDeleted()) {
            return;
        }
        message.delete(clock.instant());
        events.publish(members.findUsernames(conversationId),
                new ChatEvent("message.deleted", conversationId, MessageView.of(message)));
    }

    private Message ownMessage(AuthUser me, UUID conversationId, UUID messageId) {
        conversationService.requireMembership(me.id(), conversationId);
        Message message = messages.findInConversation(conversationId, messageId)
                .orElseThrow(() -> ApiException.notFound("Message"));
        if (!message.getSender().getId().equals(me.id())) {
            throw ApiException.forbidden("You can only change your own messages");
        }
        return message;
    }
}
