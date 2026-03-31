package com.example.chat.modules.message.application.mapper;

import com.example.chat.modules.message.application.dto.request.AttachmentRequest;
import com.example.chat.modules.message.domain.model.AttachmentDraft;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AttachmentDraftMapper {

    public List<AttachmentDraft> map(
            List<AttachmentRequest> attachments
    ) {

        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        return attachments.stream()
                .map(a -> AttachmentDraft.builder()
                        .type(a.getType())
                        .url(a.getUrl())
                        .publicId(a.getPublicId())
                        .fileName(a.getFileName())
                        .size(a.getSize())
                        .width(a.getWidth())
                        .height(a.getHeight())
                        .duration(a.getDuration())
                        .build()
                )
                .toList();
    }
}