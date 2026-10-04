package com.myproject.app.messenger.chat;

import com.myproject.app.messenger.user.UserDtos.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    public static final int MAX_GROUP_SIZE = 50;

    private ChatDtos() {
    }

    public record CreateDirectRequest(@NotBlank String username) {
    }

    public record CreateGroupRequest(
            @NotBlank @Size(max = 100) String title,
            @NotEmpty @Size(max = MAX_GROUP_SIZE - 1) List<@NotBlank String> members) {
    }

    public record RenameRequest(@NotBlank @Size(max = 100) String title) {
    }

    public record AddMemberRequest(@NotBlank String username) {
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 4000) String body,
            /* Optional client-generated id; resending the same id returns the original message instead of a duplicate. */
            @Size(max = 64) String clientMessageId) {
    }

    public record EditMessageRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record ReadRequest(@PositiveOrZero long seq) {
    }

    public record MemberView(UUID id, String username, String displayName, MemberRole role, long lastReadSeq) {
    }

    public record MessageView(UUID id, UUID conversationId, long seq, UserView sender, String body,
            Instant createdAt, Instant editedAt, boolean deleted) {

        static MessageView of(Message m) {
            return new MessageView(m.getId(), m.getConversation().getId(), m.getSeq(), UserView.of(m.getSender()),
                    m.getBody(), m.getCreatedAt(), m.getEditedAt(), m.isDeleted());
        }
    }

    /** {@code title} is the group title, or the other participant's name for a direct conversation. */
    public record ConversationView(UUID id, ConversationType type, String title, List<MemberView> members,
            long lastSeq, long unreadCount, MessageView lastMessage, Instant lastMessageAt, Instant createdAt) {
    }

    /** Messages in ascending order. Pass {@code nextBefore} as {@code before} to load older ones; null when there are none. */
    public record MessagePage(List<MessageView> messages, Long nextBefore) {
    }

    public record ReadState(UUID conversationId, long lastReadSeq, long unreadCount) {
    }
}
