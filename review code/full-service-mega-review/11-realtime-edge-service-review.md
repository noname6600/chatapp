# Realtime Edge Service Review

**Service**: Unified WebSocket Gateway (Port 8089)  
**Responsibility**: Consolidated WebSocket endpoint for realtime events  
**Tech Stack**: Spring Boot, WebSocket, Redis, Kafka, JWT  
**Status**: Early-stage consolidation endpoint (new)

---

## Critical Issues

| Issue | Severity | Impact | Note |
|-------|----------|--------|------|
| Duplicate @Slf4j annotations | MEDIUM | Compile warnings | 3 classes affected |
| Stale package references | LOW | Dead code | com.example.realtimeedge |
| Kafka constants | BLOCKER | Runtime errors | Add to KafkaTopics |
| JWT handshake auth | MEDIUM | Verify implementation | Check JwtHandshakeInterceptor |

---

## 🟡 MEDIUM: Duplicate @Slf4j Annotations

**Warnings in Build**:
```
RedisEventListener.java:25: warning: Field 'log' already exists.
JwtHandshakeInterceptor.java:28: warning: Field 'log' already exists.
FriendshipKafkaEventConsumer.java:27: warning: Field 'log' already exists.
```

**Cause**: Class AND parent/inner class both have `@Slf4j` from Lombok

**Fix**: Remove duplicate `@Slf4j` from one level

```java
// ❌ Bad - both parent and child annotated
@Slf4j
public class BaseHandler {
    // ...
}

@Slf4j  // ❌ Remove this
public class SpecialHandler extends BaseHandler {
    // ...
}

// ✅ Good - only parent annotated
@Slf4j
public class SpecialHandler extends BaseHandler {
    // ...
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW  
**Verification**: `./gradlew.bat :realtime-edge-service:compileJava --no-daemon` (warnings should disappear)

---

## 🟡 LOW: Stale Package References

**Issue**: Dead code in `com.example.realtimeedge` package (old namespace?)

**Action**: Verify if any code still references this package; if not, delete

**Scope**: SERVICE-ONLY (cleanup)  
**Risk**: NONE

---

## 🔴 BLOCKER: Kafka Topic Constants Missing

(Same as other services)

**Required Constants**:
- Event types that realtime-edge listens to (friendship, chat, presence, etc.)

**Fix**: Add all event types to `common-events/KafkaTopics.java`

**Scope**: COMMON-REQUIRED  
**Risk**: LOW

---

## 🟡 MEDIUM: JWT Handshake Authentication

**Location**: `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java`

**Verify**:
1. WebSocket connection requires valid JWT token
2. Token extracted from `Authorization` header or query parameter
3. Token validated before connection accepted
4. Invalid token → connection rejected

**Expected Behavior**:
```java
@Component
public class JwtHandshakeInterceptor extends HandshakeInterceptor {
    
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, 
                                  ServerHttpResponse response,
                                  WebSocketHandler wsHandler,
                                  Map<String, Object> attributes) {
        
        // Extract JWT from header
        String token = extractToken(request);
        if (token == null) {
            return false;  // ❌ Reject
        }
        
        try {
            // Validate JWT signature
            Claims claims = jwtDecoder.validateToken(token);
            
            // Store userId in session
            attributes.put("userId", claims.getSubject());
            
            return true;  // ✅ Accept
        } catch (JwtException e) {
            return false;  // ❌ Reject invalid token
        }
    }
}
```

**Risk**: If not implemented, WebSocket allows unauthenticated connections (HIGH security issue)

**Verification**:
```bash
# Should fail
wscat -c ws://localhost:8089/ws

# Should succeed
wscat -c ws://localhost:8089/ws -H "Authorization: Bearer <valid-token>"
```

---

## Architecture Notes

### Migration Strategy (Not forcing now)

**Current State**:
- Services still own WebSocket handlers (presence, notification, etc.)
- Realtime-edge offers consolidated endpoint

**Migration Path** (DO-NOT-FIX-NOW):
1. Realtime-edge fully functional (new clients use it)
2. Gradually migrate existing clients (feature flag)
3. Eventually deprecate service-owned WebSocket handlers

**Risk**: Do not force migration; stability is priority

---

## Testing

### 🟡 MEDIUM: No WebSocket Connection Tests

**Recommendation**: Add tests
```java
@Test
public void testWebSocketConnectWithValidJwt_Success() { }

@Test
public void testWebSocketConnectWithoutJwt_Rejected() { }

@Test
public void testWebSocketConnectWithExpiredJwt_Rejected() { }

@Test
public void testReceiveEvent_MultipleSubscribers() { }
```

---

## Verification

```bash
./gradlew.bat :realtime-edge-service:compileJava --no-daemon
./gradlew.bat :realtime-edge-service:test --no-daemon
```

---

**Priority**: Fix warnings + ensure JWT auth works correctly  
**Estimated Effort**: 2-3 hours

**Next**: Read 12-cross-service-flow-review.md
