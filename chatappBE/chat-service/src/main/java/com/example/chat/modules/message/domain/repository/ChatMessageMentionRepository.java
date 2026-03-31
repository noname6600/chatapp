package com.example.chat.modules.message.domain.repository;

import com.example.chat.modules.message.domain.entity.ChatMessageMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageMentionRepository
        extends JpaRepository<ChatMessageMention, UUID> {

    List<ChatMessageMention> findByMessageId(UUID messageId);

}
