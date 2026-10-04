package com.myproject.app.messenger.realtime;

import java.util.UUID;

/**
 * Pushed to each affected user on {@code /user/queue/events}.
 * Types: message.created, message.updated, message.deleted, conversation.created, conversation.updated,
 * member.left, read, typing.
 */
public record ChatEvent(String type, UUID conversationId, Object data) {
}
