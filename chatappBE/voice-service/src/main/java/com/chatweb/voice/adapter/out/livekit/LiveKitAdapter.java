package com.chatweb.voice.adapter.out.livekit;

import com.chatweb.voice.config.LiveKitProperties;
import com.chatweb.voice.domain.port.out.LiveKitPort;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class LiveKitAdapter implements LiveKitPort {

    private static final long TOKEN_TTL_SECONDS = 600; // 10 minutes

    private final LiveKitProperties props;
    private final RoomServiceClient roomServiceClient;

    @Override
    public void createRoom(String roomName) {
        if (!props.isEnabled()) {
            log.debug("[LIVEKIT-STUB] createRoom roomName={}", roomName);
            return;
        }
        try {
            roomServiceClient.createRoom(roomName).execute();
            log.info("[LIVEKIT] Room created roomName={}", roomName);
        } catch (Exception ex) {
            log.warn("[LIVEKIT] createRoom failed roomName={} — may already exist", roomName, ex);
        }
    }

    @Override
    public void deleteRoom(String roomName) {
        if (!props.isEnabled()) {
            log.debug("[LIVEKIT-STUB] deleteRoom roomName={}", roomName);
            return;
        }
        try {
            roomServiceClient.deleteRoom(roomName).execute();
            log.info("[LIVEKIT] Room deleted roomName={}", roomName);
        } catch (Exception ex) {
            log.warn("[LIVEKIT] deleteRoom failed roomName={}", roomName, ex);
        }
    }

    @Override
    public String generateToken(String roomName, String participantIdentity, boolean canPublish, boolean canSubscribe) {
        if (!props.isEnabled()) {
            log.debug("[LIVEKIT-STUB] generateToken roomName={} identity={}", roomName, participantIdentity);
            return "stub-token-" + participantIdentity + "-" + roomName;
        }
        AccessToken token = new AccessToken(props.getApiKey(), props.getApiSecret());
        token.setName(participantIdentity);
        token.setIdentity(participantIdentity);
        token.setTtl(TOKEN_TTL_SECONDS);
        token.addGrants(new RoomJoin(true), new RoomName(roomName));
        return token.toJwt();
    }

    @Override
    public Set<String> listLiveParticipantIdentities(String roomName) {
        if (!props.isEnabled()) {
            // No real LiveKit to check against — reconciliation can't tell live from stale here.
            return null;
        }
        try {
            Response<List<LivekitModels.ParticipantInfo>> resp = roomServiceClient.listParticipants(roomName).execute();
            if (resp.isSuccessful()) {
                Set<String> identities = new HashSet<>();
                List<LivekitModels.ParticipantInfo> body = resp.body();
                if (body != null) {
                    for (LivekitModels.ParticipantInfo p : body) {
                        identities.add(p.getIdentity());
                    }
                }
                return identities;
            }
            if (resp.code() == 404) {
                // Room no longer exists on LiveKit — confirmed zero real participants.
                return Set.of();
            }
            log.warn("[LIVEKIT] listParticipants failed roomName={} code={}", roomName, resp.code());
            return null;
        } catch (Exception ex) {
            log.warn("[LIVEKIT] listParticipants error roomName={}", roomName, ex);
            return null;
        }
    }
}
