package com.example.chat.modules.message.application.pipeline.reaction;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class ToggleReactionContext {

    private UUID messageId;

    private UUID userId;

    private String emoji;

    private boolean removed;

    private Instant reactionCreatedAt;

}