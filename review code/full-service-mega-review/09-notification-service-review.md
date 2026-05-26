# Notification Service Review

**Service**: Notifications (Port 8087)  
**Responsibility**: Notifications, read state, WebSocket push  
**Tech Stack**: Spring Boot, Spring Data JPA, Postgres, Kafka, Redis, WebSocket  

---

## Critical Issues

| Issue | Severity | Location | Fix |
|-------|----------|----------|-----|
| Duplicate Kafka listeners | BLOCKER | NotificationKafkaConsumer | Consolidate listeners |
| Kafka constants undefined | BLOCKER | common-events | Add to KafkaTopics |
| Tests restored but verify | ✅ | NotificationKafkaEventApplicationServiceTest | Run tests |
| WebSocket lifecycle | MEDIUM | NotificationWebSocketHandler | Fix disconnect order |
| Notification dedup | HIGH | NotificationService | Prevent duplicates |

---

## 🔴 BLOCKER: Duplicate Kafka Listeners

**Problem**: Multiple `@KafkaListener` methods on same topic → partition race

**Location**: `notification-service/src/main/java/com/example/notification/adapter/in/kafka/`

**Check For**:
```java
@KafkaListener(topics = "notification.events", groupId = "notification-service")
public void handleChatMessageEvent(MessageCreatedEvent event) { }

@KafkaListener(topics = "notification.events", groupId = "notification-service")
public void handleUserEvent(UserCreatedEvent event) { }  // ❌ DUPLICATE
```

**Correct Pattern**:
```java
@KafkaListener(topics = "notification.events", groupId = "notification-service")
public void handleEvent(ConsumerRecord<String, String> record) {
    String eventType = record.key();
    if (eventType.equals("message.created")) {
        handleChatMessageEvent(...);
    } else if (eventType.equals("user.created")) {
        handleUserEvent(...);
    }
}
```

**Or use routing strategy**:
- Different consumer groups per event type
- Use `@KafkaListener` routing by topic pattern

**Scope**: SERVICE-ONLY  
**Risk**: LOW  

---

## 🔴 BLOCKER: Kafka Topic Constants Missing

**Add to KafkaTopics.java**:
```java
public static final String NOTIFICATION_CREATED = "notification.created";
public static final String NOTIFICATION_READ = "notification.read";
public static final String NOTIFICATION_DISMISSED = "notification.dismissed";
```

**Scope**: COMMON-REQUIRED  
**Risk**: LOW

---

## 🟠 HIGH: Notification Deduplication

**Issue**: If Kafka event is retried/replayed, duplicate notifications created

**Scenario**:
1. Chat message sent → CHAT_MESSAGE_SENT event
2. Notification service receives → creates notification
3. Kafka retries (offset not committed yet)
4. Notification service receives again → creates duplicate

**Fix**: Idempotent consumption
```java
@KafkaListener(topics = "chat.messages", groupId = "notification-service")
public void handleChatMessage(ChatMessageCreatedEvent event) {
    String eventId = event.getMetadata().getEventId();
    
    // Check if already processed
    if (notificationIdempotencyService.isProcessed(eventId)) {
        return;  // Duplicate, skip
    }
    
    Notification notif = createNotification(event);
    notificationRepository.save(notif);
    
    notificationIdempotencyService.markProcessed(eventId);
    
    // Push to connected users via WebSocket
    messagingTemplate.convertAndSend("/topic/notifications/" + event.getReceiverId(), notif);
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (requires idempotency state store)

---

## 🟠 MEDIUM: Phase 6 Tests Restored

**Status**: Tests restored ✅

**Verify**:
```bash
./gradlew.bat :notification-service:test --tests "com.example.notification.application.NotificationKafkaEventApplicationServiceTest" --no-daemon
./gradlew.bat :notification-service:test --tests "com.example.notification.kafka.NotificationKafkaConsumersTest" --no-daemon
```

**Expected**: BUILD SUCCESSFUL

---

## 🟡 MEDIUM: WebSocket Lifecycle

(Same as presence-service and chat-service)

**Ensure disconnect cleanup** before removing from session registry

**Location**: `NotificationWebSocketHandler.afterConnectionClosed()`

---

## Verification

```bash
./gradlew.bat :notification-service:compileJava --no-daemon
./gradlew.bat :notification-service:test --no-daemon
```

---

**Priority**: Fix BLOCKER (duplicate listeners, Kafka constants) + HIGH (dedup)  
**Estimated Effort**: 3-4 hours

**Next**: Read 10-upload-service-review.md
