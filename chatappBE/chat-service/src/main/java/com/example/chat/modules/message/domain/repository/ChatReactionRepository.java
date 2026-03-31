package com.example.chat.modules.message.domain.repository;

import com.example.chat.modules.message.domain.entity.ChatReaction;
import com.example.chat.modules.message.domain.repository.projection.MessageReactionCountProjection;
import com.example.chat.modules.message.domain.repository.projection.MessageReactionSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatReactionRepository
        extends JpaRepository<ChatReaction, UUID> {

    Optional<ChatReaction> findByMessageIdAndUserIdAndEmoji(
            UUID messageId,
            UUID userId,
            String emoji
    );

    void deleteByMessageIdAndUserIdAndEmoji(
            UUID messageId,
            UUID userId,
            String emoji
    );

    @Query("""
        SELECT
            r.messageId AS messageId,
            r.emoji AS emoji,
            COUNT(r.id) AS count
        FROM ChatReaction r
        WHERE r.messageId IN :messageIds
        GROUP BY r.messageId, r.emoji
    """)
    List<MessageReactionCountProjection> countReactionsForMessages(
            List<UUID> messageIds
    );

        @Query("""
                SELECT
                        r.messageId AS messageId,
                        r.emoji AS emoji,
                        COUNT(r.id) AS count,
                        CASE
                                WHEN SUM(CASE WHEN r.userId = :currentUserId THEN 1 ELSE 0 END) > 0
                                THEN true
                                ELSE false
                        END AS reactedByMe
                FROM ChatReaction r
                WHERE r.messageId IN :messageIds
                GROUP BY r.messageId, r.emoji
        """)
        List<MessageReactionSummaryProjection> summarizeReactionsForMessages(
                        List<UUID> messageIds,
                        UUID currentUserId
        );
}