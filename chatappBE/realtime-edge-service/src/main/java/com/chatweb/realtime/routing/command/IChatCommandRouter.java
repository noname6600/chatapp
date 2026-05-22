package com.chatweb.realtime.routing.command;

import java.util.Map;
import java.util.UUID;

/**
 * Routes chat commands from the edge to chat-service.
 */
public interface IChatCommandRouter {
    void sendMessage(String accessToken, Map<String, Object> payload);

    void editMessage(String accessToken, UUID messageId, Map<String, Object> payload);

    void deleteMessage(String accessToken, UUID messageId, Map<String, Object> payload);

    void toggleReaction(String accessToken, UUID messageId, String reaction);

    boolean canAccessRoom(String accessToken, UUID roomId);

    void joinRoom(String accessToken, UUID roomId);

    void leaveRoom(String accessToken, UUID roomId);

    void pinMessage(String accessToken, UUID roomId, UUID messageId);

    void unpinMessage(String accessToken, UUID roomId, UUID messageId);
}