package com.chatweb.voice.adapter.out.redis;

import com.chatweb.voice.domain.model.CallState;
import com.chatweb.voice.domain.model.CallStatus;
import com.chatweb.voice.domain.port.out.CallStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CallStateRedisAdapter implements CallStatePort {

    private static final String CALL_HASH_KEY    = "voice:call:%s";
    private static final String ACTIVE_CALL_KEY  = "voice:user:%s:active_call";
    private static final String RINGING_INDEX_KEY = "voice:calls:ringing";
    private static final String MISS_LOCK_KEY    = "voice:call:%s:miss-lock";

    private static final Duration CALL_TTL      = Duration.ofMinutes(10);
    private static final Duration MISS_LOCK_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(CallState state) {
        String key = CALL_HASH_KEY.formatted(state.callId());
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("callId",       state.callId().toString());
        hash.put("callerId",     state.callerId().toString());
        hash.put("calleeId",     state.calleeId().toString());
        hash.put("status",       state.status().name());
        hash.put("lkRoomName",   state.lkRoomName());
        hash.put("callerLkToken", state.callerLkToken() != null ? state.callerLkToken() : "");
        hash.put("createdAt",    String.valueOf(state.createdAt()));
        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, CALL_TTL);
    }

    @Override
    public Optional<CallState> findById(UUID callId) {
        String key = CALL_HASH_KEY.formatted(callId);
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        if (hash.isEmpty()) return Optional.empty();
        return Optional.of(fromHash(hash));
    }

    @Override
    public void delete(UUID callId) {
        redisTemplate.delete(CALL_HASH_KEY.formatted(callId));
        redisTemplate.delete(MISS_LOCK_KEY.formatted(callId));
    }

    @Override
    public void setActiveCall(UUID userId, UUID callId) {
        redisTemplate.opsForValue().set(ACTIVE_CALL_KEY.formatted(userId), callId.toString(), CALL_TTL);
    }

    @Override
    public Optional<UUID> getActiveCall(UUID userId) {
        String val = redisTemplate.opsForValue().get(ACTIVE_CALL_KEY.formatted(userId));
        if (val == null || val.isBlank()) return Optional.empty();
        try { return Optional.of(UUID.fromString(val)); } catch (Exception ignored) { return Optional.empty(); }
    }

    @Override
    public void clearActiveCall(UUID userId) {
        redisTemplate.delete(ACTIVE_CALL_KEY.formatted(userId));
    }

    @Override
    public boolean tryLockMissed(UUID callId) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(MISS_LOCK_KEY.formatted(callId), "1", MISS_LOCK_TTL);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void addToRingingIndex(UUID callId, long createdAt) {
        redisTemplate.opsForZSet().add(RINGING_INDEX_KEY, callId.toString(), createdAt);
    }

    @Override
    public void removeFromRingingIndex(UUID callId) {
        redisTemplate.opsForZSet().remove(RINGING_INDEX_KEY, callId.toString());
    }

    @Override
    public List<String> findRingingCallIdsBefore(long epochMs) {
        Set<String> result = redisTemplate.opsForZSet()
                .rangeByScore(RINGING_INDEX_KEY, 0, epochMs);
        return result != null ? new ArrayList<>(result) : List.of();
    }

    private CallState fromHash(Map<Object, Object> hash) {
        return new CallState(
                UUID.fromString((String) hash.get("callId")),
                UUID.fromString((String) hash.get("callerId")),
                UUID.fromString((String) hash.get("calleeId")),
                CallStatus.valueOf((String) hash.get("status")),
                (String) hash.get("lkRoomName"),
                (String) hash.getOrDefault("callerLkToken", ""),
                Long.parseLong((String) hash.get("createdAt"))
        );
    }
}
