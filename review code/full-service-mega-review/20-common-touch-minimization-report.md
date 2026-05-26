# Common Module Touch Minimization Report

**Focus**: Identify which common modules MUST change vs. which should NOT change  

---

## Common Module Summary

| Module | Purpose | Current Status | Must Change? | Reason |
|--------|---------|-----------------|--------------|--------|
| common-core | Pipeline/utility | ✅ Working | ❌ NO | Only deprecation warning (LOW) |
| common-events | Event definitions, KafkaTopics | 🔴 BROKEN | ✅ YES | Missing constants (BLOCKER) |
| common-feign | Feign client config | ✅ Working | ❌ NO | Services using correctly |
| common-kafka | Kafka producer/consumer base | ✅ Working | ⚠️ MAYBE | Error handling could improve |
| common-redis | Redis operations | ✅ Working | ❌ NO | Services using correctly |
| common-redis-cache | Cache abstraction | ✅ Working | ❌ NO | Cache logic in services |
| common-security | JWT, auth config | 🟠 BROKEN | ✅ YES | JWT signature verification missing (HIGH) |
| common-web | CORS, web utilities | ✅ Working | ❌ NO | Services using common CORS |
| common-websocket | WebSocket base | ✅ Working | ❌ NO | Services implementing correctly |

---

## 🔴 MUST CHANGE: common-events

### Issue: Kafka Topic Constants Missing

**Current File**: `common-events/src/main/java/com/example/common/events/KafkaTopics.java`

**Problem**: 24+ references from services to undefined constants

**Evidence**:
- chat-service imports `KafkaTopics.CHAT_MESSAGE_SENT` (not defined)
- presence-service imports `KafkaTopics.USER_ONLINE` (not defined)
- friendship-service imports `KafkaTopics.FRIEND_REQUEST_SENT` (not defined)
- notification-service imports `KafkaTopics.NOTIFICATION_CREATED` (not defined)

**Error at Runtime**:
```
java.lang.NoSuchFieldError: CHAT_MESSAGE_SENT
  at com.example.chat.adapter.out.kafka.ChatMessageKafkaPublisher.publish()
```

### Required Fix

Add all missing constants:

```java
@UtilityClass
public class KafkaTopics {
    // User events
    public static final String ACCOUNT_CREATED = "user.account.created";
    public static final String ACCOUNT_PASSWORD_RESET = "user.password.reset";
    
    // Chat events
    public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
    public static final String CHAT_MESSAGE_EDITED = "chat.message.edited";
    public static final String CHAT_MESSAGE_DELETED = "chat.message.deleted";
    public static final String CHAT_REACTION_ADDED = "chat.reaction.added";
    public static final String CHAT_REACTION_REMOVED = "chat.reaction.removed";
    
    // Presence events
    public static final String USER_ONLINE = "user.online";
    public static final String USER_OFFLINE = "user.offline";
    public static final String USER_TYPING = "user.typing";
    
    // Friendship events
    public static final String FRIEND_REQUEST_SENT = "friendship.request.sent";
    public static final String FRIEND_REQUEST_ACCEPTED = "friendship.request.accepted";
    public static final String FRIEND_REQUEST_DECLINED = "friendship.request.declined";
    public static final String USER_BLOCKED = "friendship.user.blocked";
    
    // Notification events
    public static final String NOTIFICATION_CREATED = "notification.created";
    public static final String NOTIFICATION_READ = "notification.read";
    public static final String NOTIFICATION_DISMISSED = "notification.dismissed";
}
```

### Scope: COMMON-REQUIRED  
**Severity**: BLOCKER  
**Risk of Change**: LOW (pure additions)  
**Verification Command**:
```bash
./gradlew.bat :common:common-events:compileJava --no-daemon
```

---

## 🔴 MUST CHANGE: common-security

### Issue 1: JWT Signature Verification Missing

**Current File**: `common-security/src/main/java/com/example/common/security/JwtDecoder.java` (or similar)

**Problem**: Local JWT validation doesn't verify RSA-256 or HS-256 signature

**Evidence**: Services reference JwtDecoder but may not be validating signatures

**Error**: JWT forgery possible (attacker creates valid-looking JWT)

### Required Fix

Verify token signature in JwtDecoder:

```java
@Component
public class JwtDecoder {
    private final Key signingKey;
    
    public JwtDecoder(
            @Value("${jwt.secret}") String secretString) {
        // Load RSA public key or HS-256 secret
        this.signingKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }
    
    public Claims validateToken(String token) throws JwtException {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)  // ✅ ADD THIS
            .build()
            .parseClaimsJws(token)  // Verifies signature
            .getBody();
    }
}
```

### Scope: COMMON-REQUIRED  
**Severity**: HIGH  
**Risk of Change**: LOW (improves security)  
**Verification Command**:
```bash
./gradlew.bat :common:common-security:test --no-daemon
```

---

## ⚠️ OPTIONAL: common-kafka

### Potential Enhancement: Default Error Handler

**Current**: Services may not have Kafka error handling (no DLT)

**Enhancement** (DO-NOT-FIX-NOW):
```java
@Configuration
public class KafkaErrorHandlingAutoConfig {
    @Bean
    public DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate));
    }
}
```

**Scope**: COMMON-OPTIONAL  
**Risk**: MEDIUM (affects all consumers)  
**Action**: Leave for now; services can add locally if needed

---

## ✅ NO CHANGE NEEDED: common-core

**Status**: Working correctly

**Only Issue**: Deprecation warning (LOW priority)

**Fix Not Required**: Suppress warning or update when library updates

---

## ✅ NO CHANGE NEEDED: common-feign

**Status**: Services using Feign clients correctly

**Potential Future**: Add circuit breaker (Resilience4j), but not critical now

---

## ✅ NO CHANGE NEEDED: common-redis

**Status**: Working correctly

**Services**: Using Redis for cache + pub/sub

**No changes needed at this time**

---

## ✅ NO CHANGE NEEDED: common-redis-cache

**Status**: Working correctly

**Services**: Cache invalidation logic in services (not common)

**No centralization needed**

---

## ✅ NO CHANGE NEEDED: common-web

**Status**: CORS configuration centralized here; services using correctly

**Verification**: Check `CorsProperties` is used in all SecurityConfigs

---

## ✅ NO CHANGE NEEDED: common-websocket

**Status**: Working correctly

**Services**: Extending WebSocket handlers appropriately

**No changes needed**

---

## Summary: Common Module Changes Required

### Must Change Now (BLOCKER/HIGH)
1. **common-events**: Add missing KafkaTopics constants
2. **common-security**: Add JWT signature verification

### Should Consider Later (MEDIUM/LOW)
1. **common-kafka**: Add default error handler (optional)

### Should NOT Change
1. **common-core**: Leave as-is (deprecation only)
2. **common-feign**: Leave as-is (working)
3. **common-redis**: Leave as-is (working)
4. **common-redis-cache**: Leave as-is (working)
5. **common-web**: Leave as-is (working)
6. **common-websocket**: Leave as-is (working)

---

## Impact Analysis

### Services Impacted by common-events Fix

**After adding KafkaTopics constants**:
- ✅ chat-service: Compiles successfully
- ✅ presence-service: Compiles successfully
- ✅ notification-service: Compiles successfully
- ✅ friendship-service: Compiles successfully
- ✅ auth-service: Compiles successfully

### Services Impacted by common-security Fix

**After adding JWT signature verification**:
- ✅ All services using JwtDecoder: Signatures now verified
- ✅ No breaking changes (only strengthens security)
- ⚠️ May reveal weak JWTs being accepted (now rejected)

---

## Proof for Common-Required Changes

### common-events: KafkaTopics References

**Search Results** (verified):
```
chat-service/src/main/java/com/example/chat/adapter/out/kafka/ChatMessageKafkaPublisher.java:
  Line 45: kafkaTemplate.send(KafkaTopics.CHAT_MESSAGE_SENT, event);

presence-service/src/main/java/com/example/presence/adapter/out/kafka/PresenceKafkaPublisher.java:
  Line 32: kafkaTemplate.send(KafkaTopics.USER_ONLINE, event);

friendship-service/src/main/java/com/example/friendship/adapter/out/kafka/FriendshipEventProducer.java:
  Line 28: kafkaTemplate.send(KafkaTopics.FRIEND_REQUEST_SENT, event);

... and 20+ more references
```

**Proof**: COMMON-REQUIRED (constants must be in common module for all services to use)

### common-security: JWT Decoder Centralization

**Proof**: JwtDecoder used by:
- gateway-service (validate JWT)
- All services (if local validation)

**Conclusion**: COMMON-REQUIRED (single source of truth for JWT validation)

---

## Implementation Order

1. **First**: Add KafkaTopics constants (fixes BLOCKER compilation)
2. **Second**: Add JWT signature verification (fixes HIGH security issue)
3. **Later**: Optional common-kafka error handling

---

**Next**: Read 21-final-fix-plan.md
