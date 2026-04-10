package com.example.chat.modules.message.application.mapper;

import com.example.chat.modules.message.application.dto.request.AttachmentRequest;
import com.example.chat.modules.message.application.dto.request.MessageBlockRequest;
import com.example.chat.modules.message.application.dto.request.RoomInviteRequest;
import com.example.chat.modules.message.application.dto.response.AttachmentResponse;
import com.example.chat.modules.message.application.dto.response.MessageBlockResponse;
import com.example.chat.modules.message.application.dto.response.RoomInviteResponse;
import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageBlockType;
import com.example.chat.modules.message.domain.model.AttachmentDraft;
import com.example.common.integration.chat.AttachmentPayload;
import com.example.common.integration.chat.MessageBlockPayload;
import com.example.common.integration.chat.RoomInvitePayload;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageBlockMapper {

    private static final TypeReference<List<MessageBlockRequest>> BLOCK_LIST_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public boolean hasBlocks(List<MessageBlockRequest> blocks) {
        return blocks != null && !blocks.isEmpty();
    }

    public List<AttachmentDraft> toAttachmentDrafts(List<MessageBlockRequest> blocks) {
        List<MessageBlockRequest> normalized = normalizeBlocks(blocks);

        return normalized.stream()
                .filter(block -> block.getType() == MessageBlockType.ASSET)
                .map(block -> toAttachmentDraft(block.getAttachment()))
                .toList();
    }

    public String buildContentText(List<MessageBlockRequest> blocks) {
        List<MessageBlockRequest> normalized = normalizeBlocks(blocks);

        return normalized.stream()
                .filter(block -> block.getType() == MessageBlockType.TEXT)
                .map(MessageBlockRequest::getText)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(" "));
    }

    public String encodeBlocks(List<MessageBlockRequest> blocks) {
        List<MessageBlockRequest> normalized = normalizeBlocks(blocks);

        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize message blocks", exception);
        }
    }

    public List<MessageBlockResponse> toResponses(String blocksJson) {
        List<MessageBlockRequest> decoded = decodeBlocks(blocksJson);
        if (decoded.isEmpty()) {
            return Collections.emptyList();
        }

        return decoded.stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MessageBlockPayload> toPayloads(String blocksJson) {
        List<MessageBlockRequest> decoded = decodeBlocks(blocksJson);
        if (decoded.isEmpty()) {
            return Collections.emptyList();
        }

        return decoded.stream()
                .map(this::toPayload)
                .toList();
    }

    public String buildPreviewText(String blocksJson) {
        List<MessageBlockRequest> decoded = decodeBlocks(blocksJson);
        if (decoded.isEmpty()) {
            return "";
        }

        return decoded.stream()
                .map(block -> switch (block.getType()) {
                    case TEXT -> block.getText() == null ? "" : block.getText().trim();
                    case ASSET -> labelForAttachment(block.getAttachment());
                    case ROOM_INVITE -> labelForInvite(block.getRoomInvite());
                })
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "));
    }

    private List<MessageBlockRequest> normalizeBlocks(List<MessageBlockRequest> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MESSAGE_CONTENT_EMPTY,
                    "Message blocks cannot be empty"
            );
        }

        List<MessageBlockRequest> normalized = blocks.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeBlock)
                .toList();

        if (normalized.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MESSAGE_CONTENT_EMPTY,
                    "Message blocks cannot be empty"
            );
        }

        return normalized;
    }

    private MessageBlockRequest normalizeBlock(MessageBlockRequest block) {
        if (block.getType() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Block type is required"
            );
        }

        return switch (block.getType()) {
            case TEXT -> normalizeTextBlock(block);
            case ASSET -> normalizeAssetBlock(block);
            case ROOM_INVITE -> normalizeRoomInviteBlock(block);
        };
    }

    private MessageBlockRequest normalizeTextBlock(MessageBlockRequest block) {
        String text = block.getText() == null ? "" : block.getText();
        if (text.isBlank()) {
            throw new BusinessException(
                    ErrorCode.MESSAGE_CONTENT_EMPTY,
                    "Text block cannot be empty"
            );
        }

        return MessageBlockRequest.builder()
                .type(MessageBlockType.TEXT)
                .text(text)
                .build();
    }

    private MessageBlockRequest normalizeAssetBlock(MessageBlockRequest block) {
        if (block.getAttachment() == null) {
            throw new BusinessException(
                    ErrorCode.ATTACHMENT_INVALID,
                    "Asset block requires attachment"
            );
        }

        AttachmentRequest attachment = block.getAttachment();

        return MessageBlockRequest.builder()
                .type(MessageBlockType.ASSET)
                .attachment(AttachmentRequest.builder()
                        .type(attachment.getType())
                        .url(attachment.getUrl())
                        .publicId(attachment.getPublicId())
                        .fileName(attachment.getFileName())
                        .size(attachment.getSize())
                        .width(attachment.getWidth())
                        .height(attachment.getHeight())
                        .duration(attachment.getDuration())
                        .build())
                .build();
    }

    private AttachmentDraft toAttachmentDraft(AttachmentRequest attachment) {
        return AttachmentDraft.builder()
                .type(attachment.getType())
                .url(attachment.getUrl())
                .publicId(attachment.getPublicId())
                .fileName(attachment.getFileName())
                .size(attachment.getSize())
                .width(attachment.getWidth())
                .height(attachment.getHeight())
                .duration(attachment.getDuration())
                .build();
    }

            private MessageBlockRequest normalizeRoomInviteBlock(MessageBlockRequest block) {
            if (block.getRoomInvite() == null || block.getRoomInvite().getRoomId() == null) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Room invite block requires roomId"
                );
            }

            RoomInviteRequest roomInvite = block.getRoomInvite();

            return MessageBlockRequest.builder()
                .type(MessageBlockType.ROOM_INVITE)
                .roomInvite(RoomInviteRequest.builder()
                    .roomId(roomInvite.getRoomId())
                    .roomName(roomInvite.getRoomName())
                    .roomAvatarUrl(roomInvite.getRoomAvatarUrl())
                    .memberCount(roomInvite.getMemberCount())
                    .build())
                .build();
            }

    private MessageBlockResponse toResponse(MessageBlockRequest block) {
        return MessageBlockResponse.builder()
                .type(block.getType())
                .text(block.getText())
                .attachment(toAttachmentResponse(block.getAttachment()))
                .roomInvite(toRoomInviteResponse(block.getRoomInvite()))
                .build();
    }

    private RoomInviteResponse toRoomInviteResponse(RoomInviteRequest roomInvite) {
        if (roomInvite == null) {
            return null;
        }

        return RoomInviteResponse.builder()
                .roomId(roomInvite.getRoomId())
                .roomName(roomInvite.getRoomName())
                .roomAvatarUrl(roomInvite.getRoomAvatarUrl())
                .memberCount(roomInvite.getMemberCount())
                .build();
    }

    private AttachmentResponse toAttachmentResponse(AttachmentRequest attachment) {
        if (attachment == null) {
            return null;
        }

        return AttachmentResponse.builder()
                .type(attachment.getType())
                .url(attachment.getUrl())
                .publicId(attachment.getPublicId())
                .fileName(attachment.getFileName())
                .size(attachment.getSize())
                .width(attachment.getWidth())
                .height(attachment.getHeight())
                .duration(attachment.getDuration())
                .build();
    }

    private MessageBlockPayload toPayload(MessageBlockRequest block) {
        return MessageBlockPayload.builder()
                .type(block.getType().name())
                .text(block.getText())
                .attachment(toAttachmentPayload(block.getAttachment()))
                .roomInvite(toRoomInvitePayload(block.getRoomInvite()))
                .build();
    }

    private RoomInvitePayload toRoomInvitePayload(RoomInviteRequest roomInvite) {
        if (roomInvite == null) {
            return null;
        }

        return RoomInvitePayload.builder()
                .roomId(roomInvite.getRoomId())
                .roomName(roomInvite.getRoomName())
                .roomAvatarUrl(roomInvite.getRoomAvatarUrl())
                .memberCount(roomInvite.getMemberCount())
                .build();
    }

    private AttachmentPayload toAttachmentPayload(AttachmentRequest attachment) {
        if (attachment == null) {
            return null;
        }

        return AttachmentPayload.builder()
                .type(toEventAttachmentType(attachment.getType()))
                .url(attachment.getUrl())
                .publicId(attachment.getPublicId())
                .fileName(attachment.getFileName())
                .size(attachment.getSize())
                .width(attachment.getWidth())
                .height(attachment.getHeight())
                .duration(attachment.getDuration())
                .build();
    }

    private com.example.common.integration.enums.AttachmentType toEventAttachmentType(AttachmentType type) {
        return switch (type) {
            case IMAGE -> com.example.common.integration.enums.AttachmentType.IMAGE;
            case VIDEO -> com.example.common.integration.enums.AttachmentType.VIDEO;
            case FILE -> com.example.common.integration.enums.AttachmentType.FILE;
        };
    }

    private String labelForAttachment(AttachmentRequest attachment) {
        if (attachment == null || attachment.getType() == null) {
            return "[Attachment]";
        }

        return switch (attachment.getType()) {
            case IMAGE -> "[Image]";
            case VIDEO -> "[Video]";
            case FILE -> "[File]";
        };
    }

    private String labelForInvite(RoomInviteRequest roomInvite) {
        if (roomInvite == null) {
            return "[Group Invite]";
        }

        String name = roomInvite.getRoomName();
        if (name == null || name.isBlank()) {
            return "[Group Invite]";
        }

        return "[Group Invite: " + name.trim() + "]";
    }

    private List<MessageBlockRequest> decodeBlocks(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(blocksJson, BLOCK_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize message blocks", exception);
        }
    }
}