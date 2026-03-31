package com.example.chat.modules.room.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LastMessagePreview {

    private UUID id;

    private UUID senderId;

    private String content;

    private Instant createdAt;
}
