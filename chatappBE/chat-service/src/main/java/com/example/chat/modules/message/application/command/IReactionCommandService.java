package com.example.chat.modules.message.application.command;


import java.util.UUID;

public interface IReactionCommandService {

    void toggleReaction(
            UUID messageId,
            UUID userId,
            String reaction
    );
}
