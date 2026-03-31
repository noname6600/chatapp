package com.example.chat.modules.message.domain.repository;

import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.common.integration.enums.AttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatAttachmentRepository
        extends JpaRepository<ChatAttachment, UUID> {

    List<ChatAttachment> findByMessageId(UUID messageId);

    List<ChatAttachment> findByMessageIdAndType(UUID messageId, AttachmentType type);

    List<ChatAttachment> findByMessageIdIn(List<UUID> messageIds);

    List<ChatAttachment> findByMessageIdInAndType(List<UUID> messageIds, AttachmentType type);
}
