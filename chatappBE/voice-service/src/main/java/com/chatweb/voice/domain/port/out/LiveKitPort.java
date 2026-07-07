package com.chatweb.voice.domain.port.out;

public interface LiveKitPort {

    void createRoom(String roomName);

    void deleteRoom(String roomName);

    String generateToken(String roomName, String participantIdentity, boolean canPublish, boolean canSubscribe);
}
