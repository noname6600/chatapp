package com.example.chat.modules.message.application.dto.response;

import com.example.chat.modules.message.domain.enums.AttachmentType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponse {
    private UUID id;
    private AttachmentType type;
    private String url;
    private String publicId;
    private String fileName;
    private Long size;
    private Integer width;
    private Integer height;
    private Integer duration;
    private Instant createdAt;

}
