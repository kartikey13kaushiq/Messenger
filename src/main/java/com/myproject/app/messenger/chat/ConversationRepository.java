package com.myproject.app.messenger.chat;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Serialises senders in one conversation so sequence numbers are gap-free and strictly ordered. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(UUID id);

    Optional<Conversation> findByDirectKey(String directKey);

    /** Race-free get-or-create for the single direct conversation between two users. */
    @Modifying
    @Query(value = """
            insert into conversations (id, type, direct_key, last_seq, created_at)
            values (:id, 'DIRECT', :directKey, 0, :now)
            on conflict (direct_key) do nothing""", nativeQuery = true)
    int insertDirectIfAbsent(UUID id, String directKey, Instant now);
}
