# Backend Legacy Code & Mismatch Audit Report
**Generated**: May 12, 2026  
**Scope**: chatappBE (excluding realtime-edge-service and chatappBE/common from main search)  
**Searched Services**: auth-service, user-service, chat-service, friendship-service, notification-service, presence-service, upload-service, gateway-service

---

## EXECUTIVE SUMMARY

The backend codebase has **CRITICAL** issues blocking compilation and multiple high-severity legacy code patterns:

| Issue Type | Count | Severity | Blocker |
|-----------|-------|----------|---------|
| KafkaTopics constant mismatches | 24 references | **CRITICAL** | ✓ YES |
| Jackson dependency missing | 2 files | **CRITICAL** | ✓ YES |
| Legacy Kafka event imports | 29 files | HIGH | After Jackson fix |
| Redis old message models | 15+ files | MEDIUM | No |
| Duplicate publisher implementations | 2 interfaces | MEDIUM | No |
| Kafka consumer group configs | 8+ listeners | LOW | No |

---

## 1. LEGACY KAFKA EVENT IMPORTS
### Pattern: `import com.example.common.integration.kafka.event.*`

**29 files importing legacy event classes** across multiple services. These should be replaced with event envelopes.

### Files by Service:

#### auth-service (1 file)
- [AccountCreatedEventProducer.java](chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java) - imports `AccountCreatedEvent`

#### user-service (1 file)
- [AccountCreatedConsumer.java](chatappBE/user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java) - imports `AccountCreatedEvent`
- Test: `AccountCreatedConsumerTest.java` - imports `AccountCreatedEvent`

#### chat-service (8 files)
**Publishers:**
- [KafkaChatMessageEventPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java) - imports `ChatMessageDeletedEvent`, `ChatMessageEditedEvent`, `ChatMessageSentEvent`
- [KafkaReactionEventPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java) - imports `ChatReactionUpdatedEvent`

**Consumers:**
- [KafkaChatMessageEventConsumer.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java) - imports `ChatMessageDeletedEvent`, `ChatMessageEditedEvent`, `ChatMessageSentEvent`
- [KafkaReactionEventConsumer.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventConsumer.java) - imports `ChatReactionUpdatedEvent`

**Tests:**
- `KafkaRealtimeCorrelationPropagationTest.java` - imports `ChatMessageSentEvent`, `ChatReactionUpdatedEvent`

#### friendship-service (4 files)
**Publishers:**
- [FriendshipEventProducer.java](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java) - imports `FriendRequestKafkaEvent`, `FriendshipEvent`

**Consumers:**
- [FriendshipRequestEventConsumer.java](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java) - imports `FriendRequestKafkaEvent`
- [FriendshipEventConsumer.java](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java) - imports `FriendshipEvent`

**Tests:**
- `FriendshipRealtimeConsumerTest.java` - imports `FriendRequestKafkaEvent`, `FriendshipEvent`

#### notification-service (9 files)
**Publishers:**
- [NotificationEventProducer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java) - imports `NotificationRequestedEvent`

**Consumers:**
- [MessageCreatedEventConsumer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java) - imports `ChatMessageSentEvent`
- [ReactionEventConsumer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java) - imports `ChatReactionUpdatedEvent`
- [FriendRequestEventConsumer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java) - imports `FriendRequestKafkaEvent`
- [ChatMessageEventConsumer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java) - imports `ChatMessageSentEvent`
- [AccountCreatedEventConsumer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java) - imports `AccountCreatedEvent`

**Tests:**
- `ReactionEventConsumerTest.java` - imports `ChatReactionUpdatedEvent`
- `MessageCreatedEventConsumerTest.java` - imports `ChatMessageSentEvent`
- `FriendRequestEventConsumerTest.java` - imports `FriendRequestKafkaEvent`

#### presence-service
- No legacy event imports detected (uses Redis directly)

#### gateway-service, upload-service
- No Kafka event handling detected

---

## 2. KAFKATOPICS CONSTANT MISMATCHES
### **CRITICAL BLOCKER** - References to non-existent constants

**Location**: [common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java](chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java)

### Only Defined Constants:
```java
public static final String TOPIC_FRIENDSHIP_EVENTS         = "friendship.events";
public static final String TOPIC_FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events";
public static final String TOPIC_SYSTEM_DEAD_LETTER        = "system.dead-letter";
public static final String TOPIC_SYSTEM_RETRY              = "system.retry";
```

### Non-Existent References (24 instances):

#### 1. **KafkaTopics.ACCOUNT_CREATED** (does not exist)
- **3 usages** - WILL FAIL TO COMPILE:
  - [auth-service/AccountCreatedEventProducer.java:25](chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java#L25)
  - [user-service/AccountCreatedConsumer.java:26](chatappBE/user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java#L26)
  - [notification-service/AccountCreatedEventConsumer.java:18](chatappBE/notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java#L18)

#### 2. **KafkaTopics.CHAT_MESSAGE_SENT** (does not exist)
- **3 usages** - WILL FAIL TO COMPILE:
  - [notification-service/MessageCreatedEventConsumer.java:33](chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java#L33)
  - [notification-service/ChatMessageEventConsumer.java:15](chatappBE/notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java#L15)
  - [chat-service/KafkaChatMessageEventPublisher.java:70](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java#L70)
  - [chat-service/KafkaChatMessageEventConsumer.java:23](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java#L23)

#### 3. **KafkaTopics.CHAT_MESSAGE_EDITED** (does not exist)
- **2 usages** - WILL FAIL TO COMPILE:
  - [chat-service/KafkaChatMessageEventPublisher.java:86](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java#L86)
  - [chat-service/KafkaChatMessageEventConsumer.java:44](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java#L44)

#### 4. **KafkaTopics.CHAT_MESSAGE_DELETED** (does not exist)
- **2 usages** - WILL FAIL TO COMPILE:
  - [chat-service/KafkaChatMessageEventPublisher.java:105](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java#L105)
  - [chat-service/KafkaChatMessageEventConsumer.java:65](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java#L65)

#### 5. **KafkaTopics.CHAT_REACTION_UPDATED** (does not exist)
- **3 usages** - WILL FAIL TO COMPILE:
  - [notification-service/ReactionEventConsumer.java:24](chatappBE/notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java#L24)
  - [chat-service/KafkaReactionEventPublisher.java:33](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java#L33)
  - [chat-service/KafkaReactionEventConsumer.java:21](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventConsumer.java#L21)

#### 6. **KafkaTopics.NOTIFICATION_REQUESTED** (does not exist)
- **1 usage** - WILL FAIL TO COMPILE:
  - [notification-service/NotificationEventProducer.java:31](chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java#L31)

#### 7. **KafkaTopics.FRIENDSHIP_REQUEST_EVENTS** (does not exist)
- **2 usages** - WILL FAIL TO COMPILE:
  - [notification-service/FriendRequestEventConsumer.java:23](chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java#L23)
  - [friendship-service/FriendshipRequestEventConsumer.java:21](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java#L21)

#### 8. **KafkaTopics.FRIENDSHIP_EVENTS** (does not exist)
- **2 usages** - WILL FAIL TO COMPILE:
  - [friendship-service/FriendshipEventProducer.java:41](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java#L41)
  - [friendship-service/FriendshipEventConsumer.java:22](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java#L22)

#### 9. **KafkaTopics.CHAT_MESSAGE_SENT** (test reference)
- **1 test usage** - WILL FAIL TO COMPILE:
  - [chat-service/RealtimeContractBaselineTest.java:23](chatappBE/chat-service/src/test/java/com/example/chat/realtime/contract/RealtimeContractBaselineTest.java#L23)

### Naming Mismatch in realtime-edge-service:
- **Correctly uses**: `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS` ✓
- **Correctly uses**: `KafkaTopics.TOPIC_FRIENDSHIP_EVENTS` ✓
- But uses hardcoded strings for chat: `"chat.message.sent"`, `"notification.*"` (not using KafkaTopics)

### Resolution Guidance (from KafkaTopics.java comments):
Per documentation, should reference EventType enums instead:
- `AccountEventType.ACCOUNT_CREATED.value()` → `"account.created"`
- `ChatEventType.MESSAGE_SENT.value()` → `"chat.message.sent"`
- `ChatEventType.MESSAGE_EDITED.value()` → `"chat.message.edited"`
- `ChatEventType.MESSAGE_DELETED.value()` → `"chat.message.deleted"`
- `ChatEventType.REACTION_UPDATED.value()` → `"chat.reaction.updated"`
- `NotificationEventType.NOTIFICATION_REQUESTED.value()` → `"notification.requested"`
- `FriendshipEventType` variants for friendship events

---

## 3. REDIS MESSAGE MODELS - Legacy Patterns

### Pattern: Using old `com.example.common.redis.message.RedisMessage` directly

**15+ files** using envelope-based Redis messages (legacy model):

#### chat-service (6 files)
- [ChatRedisPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java) - Creates `RedisMessage<T>` via factory
- [ChatMessageEventPublisherAdapter.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java) - Dual publisher adapter
- **Tests**:
  - `CrossInstanceRealtimeFanoutIntegrationTest.java`
  - `RealtimeMessagingAlignmentTest.java`
  - `RealtimeContractValidatorTest.java`
  - `RedisMessageFactoryTest.java`
  - `RedisMessageSequenceServiceTest.java`

#### presence-service (9 files - ALL subscribers use RedisMessage)
All implement `RedisEventSubscriber<RedisMessage<T>>`:
- [UserTypingSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserTypingSubscriber.java)
- [UserStopTypingSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserStopTypingSubscriber.java)
- [UserStatusChangedSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserStatusChangedSubscriber.java)
- [UserOnlineSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserOnlineSubscriber.java)
- [UserOfflineSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserOfflineSubscriber.java)
- [RoomOnlineUsersSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/RoomOnlineUsersSubscriber.java)
- [RoomLeaveSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/RoomLeaveSubscriber.java)
- [RoomJoinSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/RoomJoinSubscriber.java)
- [PresenceRedisPublisher.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java)

#### chat-service realtime subscribers (6 files)
All implement `RedisEventSubscriber<RedisMessage<T>>`:
- [ChatReactionUpdatedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatReactionUpdatedRedisSubscriber.java)
- [ChatMessageUnpinnedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageUnpinnedRedisSubscriber.java)
- [ChatMessageSentRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageSentRedisSubscriber.java)
- [ChatMessagePinnedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessagePinnedRedisSubscriber.java)
- [ChatMessageEditedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageEditedRedisSubscriber.java)
- [ChatMessageDeletedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageDeletedRedisSubscriber.java)

### RedisMessageFactory Usage:
- [ChatRedisPublisher.java:161](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java#L161) - `redisMessageFactory.create(eventType, payload)`

---

## 4. DUPLICATE/COMPETING PUBLISHER IMPLEMENTATIONS

### Two Publisher Interfaces with Multiple Implementations in chat-service:

#### Interface 1: IMessageEventPublisher
**File**: [chat-service/.../IMessageEventPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/service/IMessageEventPublisher.java)

**Implementation 1: Kafka-based**
- **Class**: [KafkaChatMessageEventPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java)
- **Service**: chat-service
- **Methods**: `sendMessage()`, `editMessage()`, `deleteMessage()`
- **Dependency**: `KafkaEventProducer`

**Implementation 2: Adapter (Redis + Legacy)**
- **Class**: [ChatMessageEventPublisherAdapter.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java)
- **Service**: chat-service
- **Implements**: Both `IMessageEventPublisher` AND `IReactionEventPublisher`
- **Dependency**: `redisPublisher`
- **Note**: Dual-role implementation combining message and reaction publishing

#### Interface 2: IReactionEventPublisher
**File**: [chat-service/.../IReactionEventPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/service/IReactionEventPublisher.java)

**Implementation 1: Kafka-based**
- **Class**: [KafkaReactionEventPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java)
- **Service**: chat-service
- **Dependency**: `KafkaEventProducer`

**Implementation 2: Adapter (shared with above)**
- **Class**: [ChatMessageEventPublisherAdapter.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java)
- **Note**: Same adapter implements both interfaces

**Issue**: Unclear which implementation is active. Both Kafka and Redis adapters could potentially be registered, creating routing ambiguity.

---

## 5. KAFKA PRODUCER AND CONSUMER BEAN DEFINITIONS

### Producer Configuration:

#### common-kafka (Auto-Configuration)
- **File**: [KafkaAutoConfiguration.java](chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java)
- **@Bean `kafkaEventPublisher()`**: Returns `DefaultKafkaEventPublisher`
  - Depends on: `KafkaTemplate<String, Object>`, `KafkaEventObserver`
  - Condition: `@ConditionalOnClass(KafkaTemplate.class)`

- **Producer class**: [DefaultKafkaEventPublisher.java](chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java)
  - Uses: `KafkaTemplate.send(topic, key, envelope)`
  - Validates envelope metadata before publishing

- **Alternative producer**: [DefaultKafkaEventProducer.java](chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java)
  - Direct `KafkaTemplate` usage
  - Also registered in auto-configuration

#### Service-level configurations:
- **user-service**: [KafkaConsumerConfig.java](chatappBE/user-service/src/main/java/com/example/user/kafka/KafkaConsumerConfig.java)
  - Injects `KafkaTemplate<String, Object>` for DLQ recovery
  
- **notification-service**: [KafkaConsumerConfig.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/KafkaConsumerConfig.java)
  - Similar DLQ configuration

### Consumer Configuration:

#### Kafka Listener Groups (8 consumer groups):
1. **"notification-service"** (2 listeners)
   - [MessageCreatedEventConsumer.java:33](chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java#L33)
   - [ReactionEventConsumer.java:24](chatappBE/notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java#L24)

2. **"realtime-edge"** (2 listeners, autoStartup=false)
   - [KafkaEventConsumer.java:28, :50](chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java)

3. **"chat-service-realtime-fanout"** (3 listeners)
   - [KafkaReactionEventConsumer.java:22](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventConsumer.java#L22)
   - [KafkaChatMessageEventConsumer.java:24, :45, :66](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java)

4. **No explicit groupId** (3 listeners)
   - [FriendRequestEventConsumer.java (notification)](chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java#L23)
   - [FriendshipRequestEventConsumer.java (friendship)](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java#L21)
   - [FriendshipEventConsumer.java (friendship)](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java#L22)

**Issue**: 3 listeners without explicit groupId may cause default groupId assignment or conflicts.

---

## 6. REDIS PUBLISHER/SUBSCRIBER IMPLEMENTATIONS

### Publisher Implementations:

#### 1. Default Redis Event Publisher (common-redis)
- **File**: [DefaultRedisEventPublisher.java](chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java)
- **Interface**: `RedisEventPublisher`
- **Implementation**: Spring Data Redis based
  - Uses: `StringRedisTemplate.convertAndSend(channel, payload)`
  - Validates envelope before publishing
  - Auto-configured in [RedisAutoConfiguration.java](chatappBE/common/common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java)

#### 2. Notification Redis Publisher
- **File**: [RedisNotificationPublisher.java](chatappBE/notification-service/src/main/java/com/example/notification/websocket/redis/RedisNotificationPublisher.java)
- **Custom implementation** for notification-specific channels
  - Uses: `StringRedisTemplate.convertAndSend()`
  - Channel: `NotificationRedisChannels.userChannel(userId)`

#### 3. Chat Redis Publisher
- **File**: [ChatRedisPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java)
- **Wrapper** around `redisPublisher`
  - Uses: `RedisMessageFactory` to create envelope
  - Creates: `RedisMessage<T>` instances
  - Methods: `publishMessageSent()`, `publishMessageEdited()`, `publishMessageDeleted()`, `publishReactionUpdated()`, `publishMessagePinned()`, `publishMessageUnpinned()`

#### 4. Presence Redis Publisher
- **File**: [PresenceRedisPublisher.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java)
- **Envelope-based** publisher
  - Creates: `RedisMessage<T>` instances
  - Methods: `publishUserOnline()`, `publishUserOffline()`, `publishUserStatusChanged()`, etc.

### Subscriber Implementations:

All subscribers implement `RedisEventSubscriber<RedisMessage<T>>`:

#### Presence Service (9 subscribers)
- [UserTypingSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserTypingSubscriber.java) - `getEventType()` → `PresenceEventType.ROOM_TYPING.value()`
- [UserStopTypingSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserStopTypingSubscriber.java)
- [UserStatusChangedSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserStatusChangedSubscriber.java)
- [UserOnlineSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserOnlineSubscriber.java)
- [UserOfflineSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/UserOfflineSubscriber.java)
- [RoomOnlineUsersSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/RoomOnlineUsersSubscriber.java)
- [RoomLeaveSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/RoomLeaveSubscriber.java)
- [RoomJoinSubscriber.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/RoomJoinSubscriber.java)
- [PresenceRedisPublisher.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java) (also acts as publisher)

#### Chat Service (6 subscribers)
- [ChatReactionUpdatedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatReactionUpdatedRedisSubscriber.java)
- [ChatMessageUnpinnedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageUnpinnedRedisSubscriber.java)
- [ChatMessageSentRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageSentRedisSubscriber.java)
- [ChatMessagePinnedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessagePinnedRedisSubscriber.java)
- [ChatMessageEditedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageEditedRedisSubscriber.java)
- [ChatMessageDeletedRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageDeletedRedisSubscriber.java)

### Channel Naming Patterns:

| Module | Channels |
|--------|----------|
| **Chat** | `realtime.chat.room.*` |
| **Presence - User** | `realtime.presence.user` |
| **Presence - Room** | `realtime.presence.room.*` |
| **Presence - Global** | `realtime.presence.global` |
| **Notification - User** | `realtime.notification.user.*` |

---

## 7. COMPILATION ERRORS AND BLOCKERS

### BLOCKER 1: Jackson Dependency Issue

**Build Task**: `:common:common-kafka:compileJava`  
**Error**:
```
error: package com.fasterxml.jackson.datatype.jsr310 does not exist
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
```

**Affected Files**:
- [EventEnvelopeKafkaDeserializer.java:12](chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaDeserializer.java#L12)
- [EventEnvelopeKafkaSerializer.java:9](chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaSerializer.java#L9)

**Severity**: **CRITICAL** - Blocks entire build

### BLOCKER 2: KafkaTopics Missing Constants (post Jackson fix)

**24 references** to non-existent `KafkaTopics.*` constants will fail:

- `KafkaTopics.ACCOUNT_CREATED` → 3 files
- `KafkaTopics.CHAT_MESSAGE_SENT` → 4 files  
- `KafkaTopics.CHAT_MESSAGE_EDITED` → 2 files
- `KafkaTopics.CHAT_MESSAGE_DELETED` → 2 files
- `KafkaTopics.CHAT_REACTION_UPDATED` → 3 files
- `KafkaTopics.NOTIFICATION_REQUESTED` → 1 file
- `KafkaTopics.FRIENDSHIP_EVENTS` → 2 files
- `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS` → 2 files
- Test reference: 1 file

**Severity**: **CRITICAL** - Blocks multiple services from compiling

**Affected Services**:
- ✗ auth-service
- ✗ user-service
- ✗ chat-service
- ✗ friendship-service
- ✗ notification-service (multiple consumers)

---

## 8. SUMMARY TABLE

| Category | Item | Count | Severity | Action Required |
|----------|------|-------|----------|-----------------|
| **Legacy Imports** | Files importing `com.example.common.integration.kafka.event.*` | 29 | HIGH | Migrate to event envelopes |
| **KafkaTopics** | Missing constant definitions | 9 types | **CRITICAL** | Add to KafkaTopics.java or use EventType enums |
| **KafkaTopics** | Incorrect references in code | 24 | **CRITICAL** | Fix references in 17 files |
| **Redis Models** | Files using old `RedisMessage` pattern | 15+ | MEDIUM | Evaluate standardization |
| **Publishers** | Duplicate `IMessageEventPublisher` implementations | 2 | MEDIUM | Consolidate strategy |
| **Publishers** | Duplicate `IReactionEventPublisher` implementations | 2 | MEDIUM | Consolidate strategy |
| **Kafka Beans** | Consumer groups without explicit ID | 3 | LOW | Add groupId or verify default behavior |
| **Jackson** | Missing dependency import | 2 | **CRITICAL** | Fix gradle dependencies |

---

## 9. SERVICE-BY-SERVICE STATUS

| Service | Status | Key Issues |
|---------|--------|-----------|
| **auth-service** | ✗ BROKEN | KafkaTopics.ACCOUNT_CREATED (missing) |
| **user-service** | ✗ BROKEN | KafkaTopics.ACCOUNT_CREATED (missing) |
| **chat-service** | ✗ BROKEN | Multiple KafkaTopics constants missing + Duplicate publishers |
| **friendship-service** | ✗ BROKEN | KafkaTopics.FRIENDSHIP_* constants missing |
| **notification-service** | ✗ BROKEN | Multiple KafkaTopics constants missing + Legacy imports |
| **presence-service** | ⚠ PARTIAL | Uses legacy RedisMessage pattern but no KafkaTopics issues |
| **gateway-service** | ✓ OK | No event handling detected |
| **upload-service** | ✓ OK | No event handling detected |
| **realtime-edge-service** | ⚠ PARTIAL | Uses KafkaTopics correctly but hardcoded topic strings |

---

## RECOMMENDED REMEDIATION ORDER

1. **Fix Jackson dependency** (CRITICAL - unblocks build)
   - Update gradle files to include Jackson JSR310 module

2. **Add missing KafkaTopics constants** (CRITICAL - enables compile)
   - Add all 9 event topic constants to KafkaTopics.java
   - OR migrate services to use EventType enums directly per pattern

3. **Remove legacy Kafka event imports** (HIGH - modernizes codebase)
   - 29 files to update to use event envelopes

4. **Consolidate publisher implementations** (MEDIUM - reduces ambiguity)
   - Decide between Kafka-first or hybrid Redis+Kafka strategy
   - Remove duplicate ChatMessageEventPublisherAdapter if needed

5. **Standardize Redis message patterns** (MEDIUM - improves consistency)
   - Review RedisMessage factory usage
   - Consider unified approach across services

6. **Fix missing Kafka consumer group IDs** (LOW - best practice)
   - 3 listeners need explicit groupId or documentation

---

## APPENDIX: File Listing

### Files with Legacy Kafka Event Imports
**Total: 29 files**

```
auth-service/
  src/main/java/.../AccountCreatedEventProducer.java
  
user-service/
  src/main/java/.../AccountCreatedConsumer.java
  src/test/java/.../AccountCreatedConsumerTest.java

chat-service/
  src/main/java/.../KafkaChatMessageEventPublisher.java
  src/main/java/.../KafkaReactionEventPublisher.java
  src/main/java/.../KafkaChatMessageEventConsumer.java
  src/main/java/.../KafkaReactionEventConsumer.java
  src/test/java/.../KafkaRealtimeCorrelationPropagationTest.java

friendship-service/
  src/main/java/.../FriendshipEventProducer.java
  src/main/java/.../FriendshipRequestEventConsumer.java
  src/main/java/.../FriendshipEventConsumer.java
  src/test/java/.../FriendshipRealtimeConsumerTest.java

notification-service/
  src/main/java/.../NotificationEventProducer.java
  src/main/java/.../MessageCreatedEventConsumer.java
  src/main/java/.../ReactionEventConsumer.java
  src/main/java/.../FriendRequestEventConsumer.java
  src/main/java/.../ChatMessageEventConsumer.java
  src/main/java/.../AccountCreatedEventConsumer.java
  src/test/java/.../ReactionEventConsumerTest.java
  src/test/java/.../MessageCreatedEventConsumerTest.java
  src/test/java/.../FriendRequestEventConsumerTest.java
```

### Critical Compilation Blockers
```
common/common-kafka/
  src/main/java/.../EventEnvelopeKafkaDeserializer.java
  src/main/java/.../EventEnvelopeKafkaSerializer.java
```

### Files with Incorrect KafkaTopics References
```
auth-service/AccountCreatedEventProducer.java:25
user-service/AccountCreatedConsumer.java:26
chat-service/KafkaChatMessageEventPublisher.java:70, 86, 105
chat-service/KafkaChatMessageEventConsumer.java:23, 44, 65
chat-service/KafkaReactionEventPublisher.java:33
chat-service/KafkaReactionEventConsumer.java:21
chat-service/RealtimeContractBaselineTest.java:23
friendship-service/FriendshipEventProducer.java:41, 66
friendship-service/FriendshipEventConsumer.java:22
friendship-service/FriendshipRequestEventConsumer.java:21
notification-service/NotificationEventProducer.java:31
notification-service/MessageCreatedEventConsumer.java:33
notification-service/ReactionEventConsumer.java:24
notification-service/ChatMessageEventConsumer.java:15
notification-service/AccountCreatedEventConsumer.java:18
notification-service/FriendRequestEventConsumer.java:23
```

