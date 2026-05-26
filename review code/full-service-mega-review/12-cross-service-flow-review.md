# Cross-Service Flow Review

**Focus**: REST calls between services, consistency guarantees, failure handling  

---

## REST Service-to-Service Calls

### Chat Service → User Service
**Flow**: Validate room membership

**Call**: `GET /api/users/{userId}` (auth required)

**Issue**: If user-service is down → chat-service requests fail

**Current**: Likely returns 503 or times out

**Recommendation**: Add circuit breaker (Resilience4j or Hystrix)
```java
@FeignClient("user-service")
@CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
public interface UserClient {
    @GetMapping("/api/users/{userId}")
    User getUser(@PathVariable String userId);
    
    User getUserFallback(String userId, Exception e) {
        return null;  // Or cached user
    }
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW  
**Classification**: DO-NOT-FIX-NOW (if working now)

---

### User Service → Auth Service
**Issue**: None documented; auth-service typically called only by gateway

---

## Kafka Event Consistency

### Scenario 1: Chat Message Sent → Notification Created

**Flow**:
1. Chat service publishes `CHAT_MESSAGE_SENT`
2. Notification service consumes → creates notification
3. User sees notification (via WebSocket or API)

**Risk**: If notification consumer fails, notification lost

**Current**: Likely no error handling or DLT

**Fix**: Add Kafka error handling
```java
@Configuration
public class KafkaErrorHandling {
    @Bean
    public DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate)
        );
    }
}
```

**Scope**: COMMON-OPTIONAL or SERVICE-ONLY  
**Risk**: MEDIUM

---

### Scenario 2: Friendship Request Sent → User Sees Notification

**Event Chain**:
1. Friendship service publishes `FRIEND_REQUEST_SENT`
2. Notification service creates notification
3. Notification service publishes notification-created event (or direct WebSocket push)

**Issue**: Double-publishing may occur (Kafka event + WebSocket event)

**Fix**: Consolidate to single notification method
- Option 1: Notification service publishes, other services consume (not subscribe directly to create)
- Option 2: Async email send via separate notification worker

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (requires careful sequencing)

---

## Redis Pub/Sub Events

### Presence Updates
**Flow**:
1. User goes online → Presence service publishes to Redis
2. Connected clients in same room receive update
3. Presence service also publishes Kafka event for offline subscribers

**Consistency**: ✅ Redis pub/sub is ephemeral (correct)

**Issue**: If multi-instance, other instances don't know about presence change

**Fix**: Already addressed by Redis pub/sub (all instances subscribe)

---

### Typing Indicators
**Flow**:
1. User types → publishes to Redis
2. Other users in room receive
3. TTL: Expires after 5 seconds

**Consistency**: ✅ Ephemeral, correct pattern

---

## Eventual Consistency & Compensation

### Scenario: Message Deleted, But Notification Already Sent

**Flow**:
1. Message created → notification sent to recipient
2. Message deleted by sender
3. Recipient sees notification but message no longer exists

**Fix Options**:
- **Option 1**: When fetching message, return 404 (client handles gracefully)
- **Option 2**: Send NOTIFICATION_INVALIDATED event → client removes notification
- **Option 3**: Don't auto-delete; soft-delete with retention (hide from UI but keep in DB)

**Recommendation**: Option 1 (simplest)

**Scope**: SERVICE-ONLY  
**Risk**: LOW

---

## Timeout & Retry Strategy

### Chat Service Calls User Service
**Timeout**: Should be 5-10 seconds (not forever)

**Retry**: Max 2 retries with exponential backoff

**Verification**: Check Feign client configuration
```java
// ✅ Good
@FeignClient(name = "user-service", 
    configuration = FeignClientConfig.class)
public interface UserClient { }

@Configuration
public class FeignClientConfig {
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
    }
}
```

**Scope**: SERVICE-ONLY or COMMON-OPTIONAL  
**Risk**: LOW

---

## Data Ownership

| Resource | Owner | Consistency |
|----------|-------|-------------|
| User profile | user-service | Strong (read-after-write) |
| Chat message | chat-service | Strong (message seq) |
| Presence | presence-service | Eventual (Redis TTL) |
| Friendship | friendship-service | Eventual (Kafka notification) |
| Notification | notification-service | Eventual (Kafka event) |

**Verification**: No service duplicates data from another service's primary store

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| No circuit breaker on Feign | MEDIUM | SERVICE-ONLY | Add resilience4j |
| Kafka error handling missing | MEDIUM | COMMON-OPTIONAL | Add DLT |
| Eventual consistency not documented | LOW | DOCUMENTATION | Update README |
| Double event publishing possible | MEDIUM | SERVICE-ONLY | Audit & fix |

---

**Next**: Read 13-realtime-websocket-kafka-redis-flow-review.md
