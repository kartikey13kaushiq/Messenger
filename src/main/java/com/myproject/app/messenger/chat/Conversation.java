package com.myproject.app.messenger.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ConversationType type;

    private String title;

    @Column(updatable = false)
    private String directKey;

    /** Sequence number of the newest message; messages are numbered 1, 2, 3... per conversation. */
    @Column(nullable = false)
    private long lastSeq;

    private Instant lastMessageAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    private Conversation(ConversationType type, String title, String directKey, Instant createdAt) {
        this.type = type;
        this.title = title;
        this.directKey = directKey;
        this.createdAt = createdAt;
    }

    public static Conversation group(String title, Instant now) {
        return new Conversation(ConversationType.GROUP, title, null, now);
    }

    public long nextSeq(Instant now) {
        lastMessageAt = now;
        return ++lastSeq;
    }

    public UUID getId() { return id; }
    public ConversationType getType() { return type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDirectKey() { return directKey; }
    public long getLastSeq() { return lastSeq; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
}
