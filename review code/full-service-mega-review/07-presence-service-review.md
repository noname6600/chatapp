# Presence Service Review

**Service**: Online Status & Typing (Port 8085)  
**Responsibility**: Online/offline state, typing indicators, room presence  
**Tech Stack**: Spring Boot, Redis (primary state), WebSocket, Kafka  

---

## Critical Issues

| Issue | Severity | Impact | Fix |
|-------|----------|--------|-----|
| Users never marked offline | BLOCKER | Stale "online" status forever | Fix WebSocket lifecycle order |
| TTL race condition | HIGH | User offline while other tab online | Reference counting |
| Kafka topic constants | BLOCKER | NoSuchFieldError at runtime | Add to KafkaTopics |
| Typing event dedup | MEDIUM | Duplicate typing notifications | Dedup in handler |

---

## 🔴 BLOCKER: WebSocket Lifecycle Bug (User Never Goes Offline)

**Problem**: Users remain "online" after disconnect because unregister happens too late

**Location**: `presence-service/src/main/java/com/example/presence/controller/PresenceWebSocketHandler.java` (or similar)

**Current Broken Code**:
```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String userId = (String) session.getAttributes().get("userId");
    
    sessionRegistry.removeSession(session);  // ❌ Removes first
    unregisterFromRoom(userId);              // ❌ Then tries to unregister (fails!)
}

private void unregisterFromRoom(String userId) {
    // Tries to find session... but it's already removed!
    // Redis entry not cleaned up
    List<WebSocketSession> sessions = sessionRegistry.getSessionsByUserId(userId);
    if (sessions.isEmpty()) {
        // Mark user offline in Redis
        redisTemplate.delete("presence:" + userId);
    }
}
```

**Fix**:
```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String userId = (String) session.getAttributes().get("userId");
    
    unregisterFromRoom(userId);              // ✅ Unregister first
    sessionRegistry.removeSession(session);  // ✅ Then remove from registry
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW  
**Verification**:
```bash
# Connect user A
# Connect user B
# Check /api/presence/room/{roomId} shows 2 users
# Disconnect user A
# Check /api/presence/room/{roomId} now shows 1 user
```

---

## 🟠 HIGH: TTL Race Condition (User "Goes Offline" While Still Online)

**Problem**: If user has 2 browser tabs:
1. Tab 1 connects → presence TTL set
2. Tab 2 connects → presence TTL reset
3. Tab 1 disconnects → unregister() called
4. Presence TTL expires (user marked offline)
5. Tab 2 still sending heartbeats but user marked offline

**Root Cause**: No reference counting

**Fix**: Use atomic counter
```java
private void registerPresence(String userId) {
    String counterKey = "presence:refcount:" + userId;
    Long count = redisTemplate.opsForValue().increment(counterKey);
    if (count == 1) {
        // First connection: set online
        redisTemplate.opsForValue().set("presence:" + userId, "online");
        publishUserOnlineEvent(userId);
    }
    // Set expiration on counter (heartbeat will refresh)
    redisTemplate.expire(counterKey, Duration.ofMinutes(5));
}

private void unregisterPresence(String userId) {
    String counterKey = "presence:refcount:" + userId;
    Long count = redisTemplate.opsForValue().decrement(counterKey);
    if (count <= 0) {
        // Last connection closed: mark offline
        redisTemplate.delete("presence:" + userId);
        publishUserOfflineEvent(userId);
    }
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (requires testing with multi-tab scenario)

---

## 🔴 BLOCKER: Kafka Topic Constants Missing

**Issue**: References to `KafkaTopics.USER_ONLINE`, `USER_OFFLINE` don't exist

**Fix**: Add to `common-events/src/main/java/com/example/common/events/KafkaTopics.java`:
```java
public static final String USER_ONLINE = "user.online";
public static final String USER_OFFLINE = "user.offline";
public static final String USER_TYPING = "user.typing";
```

**Scope**: COMMON-REQUIRED  
**Risk**: LOW

---

## 🟡 MEDIUM: Typing Event Deduplication

**Issue**: Multiple typing events for same user flood consumers

**Current**: Each keystroke sends `USER_TYPING` event

**Fix**: Add client-side debounce (e.g., send every 500ms, not on every keystroke)

**Or server-side**: Cache "last typing event" timestamp, ignore duplicates within 1 second

**Scope**: SERVICE-ONLY (client-side fix) or FRONTEND (if frontend bug)

---

## Verification

```bash
./gradlew.bat :presence-service:compileJava --no-daemon
./gradlew.bat :presence-service:test --no-daemon

# Test offline/online
docker-compose up presence-service -d
# Connect WebSocket, verify online
# Disconnect, verify offline
```

---

**Priority**: Fix BLOCKER (lifecycle, TTL) first, then Kafka constants  
**Estimated Effort**: 3-4 hours

**Next**: Read 08-friendship-service-review.md
