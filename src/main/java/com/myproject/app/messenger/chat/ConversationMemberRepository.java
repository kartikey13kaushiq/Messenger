package com.myproject.app.messenger.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    @Query("""
            select m from ConversationMember m join fetch m.conversation c
            where m.user.id = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc""")
    List<ConversationMember> findMembershipsOf(UUID userId);

    /** Owners first, then by join time; username breaks ties so the order is stable. */
    @Query("""
            select m from ConversationMember m join fetch m.user u
            where m.conversation.id in :conversationIds
            order by case when m.role = 'OWNER' then 0 else 1 end, m.joinedAt, u.username""")
    List<ConversationMember> findAllWithUsers(Collection<UUID> conversationIds);

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    long countByConversationId(UUID conversationId);

    @Query("select m.user.username from ConversationMember m where m.conversation.id = :conversationId")
    List<String> findUsernames(UUID conversationId);

    @Modifying
    @Query(value = """
            insert into conversation_members (id, conversation_id, user_id, role, last_read_seq, joined_at)
            values (gen_random_uuid(), :conversationId, :userId, 'MEMBER', 0, :now)
            on conflict (conversation_id, user_id) do nothing""", nativeQuery = true)
    void insertMemberIfAbsent(UUID conversationId, UUID userId, Instant now);
}
