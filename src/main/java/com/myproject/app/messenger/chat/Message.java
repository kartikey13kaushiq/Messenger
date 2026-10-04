package com.myproject.app.messenger.chat;

import com.myproject.app.messenger.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", updatable = false)
    private User sender;

    @Column(nullable = false, updatable = false)
    private long seq;

    private String body;

    @Column(updatable = false)
    private String clientMessageId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant editedAt;

    private Instant deletedAt;

    protected Message() {
    }

    public Message(Conversation conversation, User sender, long seq, String body, String clientMessageId, Instant createdAt) {
        this.conversation = conversation;
        this.sender = sender;
        this.seq = seq;
        this.body = body;
        this.clientMessageId = clientMessageId;
        this.createdAt = createdAt;
    }

    public void edit(String body, Instant now) {
        this.body = body;
        this.editedAt = now;
    }

    /** Soft delete: the slot (and its sequence number) stays so history and unread counts remain consistent. */
    public void delete(Instant now) {
        this.body = null;
        this.deletedAt = now;
    }

    public boolean isDeleted() { return deletedAt != null; }
    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public long getSeq() { return seq; }
    public String getBody() { return body; }
    public String getClientMessageId() { return clientMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
