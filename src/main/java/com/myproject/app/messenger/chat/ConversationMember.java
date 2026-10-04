package com.myproject.app.messenger.chat;

import com.myproject.app.messenger.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "conversation_members")
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", updatable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(nullable = false)
    private long lastReadSeq;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    protected ConversationMember() {
    }

    public ConversationMember(Conversation conversation, User user, MemberRole role, long lastReadSeq, Instant joinedAt) {
        this.conversation = conversation;
        this.user = user;
        this.role = role;
        this.lastReadSeq = lastReadSeq;
        this.joinedAt = joinedAt;
    }

    /** Read position only moves forward. Returns true if it moved. */
    public boolean markReadUpTo(long seq) {
        if (seq <= lastReadSeq) {
            return false;
        }
        lastReadSeq = seq;
        return true;
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getUser() { return user; }
    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }
    public long getLastReadSeq() { return lastReadSeq; }
    public Instant getJoinedAt() { return joinedAt; }
}
