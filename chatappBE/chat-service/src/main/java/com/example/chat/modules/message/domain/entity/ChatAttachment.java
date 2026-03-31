package com.example.chat.modules.message.domain.entity;

import com.example.chat.modules.message.domain.enums.AttachmentType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(
        name = "chat_attachments",
        indexes = {
                @Index(name = "idx_attachment_message", columnList = "messageId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAttachment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentType type;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(length = 255)
    private String publicId;

    @Column(length = 255)
    private String fileName;

    private Long size;

    private Integer width;

    private Integer height;

    private Integer duration;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}