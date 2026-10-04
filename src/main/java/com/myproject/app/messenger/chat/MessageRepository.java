package com.myproject.app.messenger.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversation.id = :conversationId and m.seq < :beforeSeq
            order by m.seq desc""")
    List<Message> findPage(UUID conversationId, long beforeSeq, Pageable page);

    @Query("""
            select m from Message m join fetch m.sender join m.conversation c
            where c.id in :conversationIds and m.seq = c.lastSeq""")
    List<Message> findLatest(Collection<UUID> conversationIds);

    @Query("select m from Message m join fetch m.sender where m.id = :id and m.conversation.id = :conversationId")
    Optional<Message> findInConversation(UUID conversationId, UUID id);

    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId,
            String clientMessageId);
}
