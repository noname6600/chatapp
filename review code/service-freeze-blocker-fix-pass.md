# Service Freeze Blocker Fix Pass

## 1. Scope
- In scope services:
  - `chatappBE/chat-service/**`
  - `chatappBE/presence-service/**`
  - `chatappBE/notification-service/**`
  - `chatappBE/friendship-service/**`
  - `chatappBE/user-service/**`
  - `chatappBE/gateway-service/**`
- Out of scope respected:
  - `chatappBE/common/**`
  - `chatappBE/auth-service/**`
  - `chatappBE/upload-service/**`
  - frontend
  - deployment/infrastructure
- Mode respected:
  - Narrow runtime/startup/wiring fixes only.
  - No broad package or architecture redesign.

## 2. Freeze Blockers Fixed

### 2.1 Websocket auto-configuration metadata startup blocker (chat/presence/notification/friendship)
- Fixed service startup failure caused by malformed websocket auto-configuration metadata entry (`U+FEFF`-prefixed class name) by excluding the exact malformed class-name token at service app bootstrap level.
- Applied in:
  - `chat-service`
  - `presence-service`
  - `notification-service`
  - `friendship-service`

### 2.2 Redis cache manager wiring mismatch (user + proactive chat alignment)
- `user-service`:
  - Aligned injected cache manager/cache interfaces to the active cache API package (`com.example.common.redis.cache.api.*`) so application wiring resolves consistently.
- `chat-service`:
  - Proactively aligned equivalent cache manager interface import in room-list cache adapter to the same cache API package.

### 2.3 Missing chat outbound publisher bean wiring
- Added a concrete outbound adapter bean that implements both:
  - `IMessageEventPublisher`
  - `IReactionEventPublisher`
- Adapter delegates to existing Redis publisher and payload factories, restoring runtime wiring for message/reaction publish steps.

### 2.4 Missing notification runtime adapter wiring
- Added concrete `NotificationRealtimePort` adapter bean that publishes user realtime events through Redis event publishing.
- Added inbound `account.created` Kafka consumer adapter delegating into `NotificationKafkaEventApplicationService`.
- Existing inbound adapters for chat/reaction/friend-request delegation were already present and remain active.

### 2.5 Missing `CorsProperties` visibility/registration in gateway
- Registered `CorsProperties` in gateway application configuration properties enablement.
- Also enabled `CorsProperties` on `GatewayConfig` for config-slice test context robustness where only `GatewayConfig` is loaded.

## 3. Files Changed
- `chatappBE/chat-service/src/main/java/com/example/chat/ChatServiceApplication.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/PresenceServiceApplication.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/NotificationServiceApplication.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/FriendshipServiceApplication.java`
- `chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/cache/redis/RedisRoomListCacheAdapter.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java` (new)
- `chatappBE/notification-service/src/main/java/com/example/notification/realtime/infrastructure/NotificationRedisRealtimeAdapter.java` (new)
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java` (new)
- `chatappBE/gateway-service/src/main/java/com/example/gateway/GatewayApplication.java`
- `chatappBE/gateway-service/src/main/java/com/example/gateway/config/GatewayConfig.java`
- `chatappBE/user-service/src/test/java/com/example/user/service/impl/UserProfileServiceUpdateProfileTest.java`
- `chatappBE/user-service/src/test/java/com/example/user/service/impl/UserProfileServiceSearchByUsernameTest.java`
- `chatappBE/user-service/src/test/java/com/example/user/service/impl/UserProfileServiceGetSelfTest.java`
- `chatappBE/user-service/src/test/java/com/example/user/service/impl/UserProfileServiceAvatarMetadataTest.java`

## 4. Validation

### 4.1 Required compile command
Executed from `chatappBE`:

`./gradlew.bat --no-daemon :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :presence-service:compileJava :notification-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava`

Result:
- `BUILD SUCCESSFUL`

### 4.2 Required test command
Executed from `chatappBE`:

`./gradlew.bat --no-daemon :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test`

Result:
- `BUILD SUCCESSFUL`

Notes:
- During earlier attempts in this session, mixed/interrupted terminal history included stale pre-fix failures. Final status above is from a clean post-fix run.
- `--continue` run was not required after clean command passed.

## 5. Remaining Blockers Only
- None identified from the required final compile/test validation.

## 6. Freeze Review Readiness
- Service layer is now ready for freeze review again.
