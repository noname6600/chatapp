## 1. Executive Summary

The codebase is on a mostly modern platform baseline: Spring Boot `3.5.6`, Java `21`, Spring Security `SecurityFilterChain` / `SecurityWebFilterChain`, and Spring Kafka `DefaultErrorHandler` are in use. I did not find active `WebSecurityConfigurerAdapter`, `antMatchers`, `authorizeRequests`, `javax.*`, or legacy Spring Kafka `SeekToCurrentErrorHandler` usage in main source.

The codebase is not clean yet. It still carries significant project-owned legacy compatibility layers in Kafka, Redis, WebSocket envelopes, presence event names, gateway routes, CORS configuration, upload contracts, and frontend local-storage / event contracts. The highest debt is not an external framework deprecation. It is that several "new" common APIs still extend, accept, or adapt the deprecated old APIs, so removing the old surface now would break active modules.

Safe removal can be done now only for isolated unused dependencies, no-op compatibility consumers, stale module fragments, and old wrapper functions with no active callers. The Redis/Kafka/WebSocket/presence/gateway/upload compatibility layers require migration first.

## 2. Deprecated Usage

### Critical

#### Presence event names are split between canonical hyphenated backend values and deprecated underscored frontend values

- Exact file/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java`
  - `chatappFE/src/constants/presenceEvents.ts`
  - `chatappFE/src/websocket/presence.socket.ts`
- Exact deprecated item:
  - `PresenceEventType` keeps `DEPRECATED_ALIASES` at lines 24-30 and `LEGACY_BY_NORMALIZED` at lines 31-36.
  - `PresenceEventType.normalize`, `isDeprecatedAlias`, `legacyAliasOf`, and warning path at lines 56-80 keep old values alive.
  - Frontend still declares old values:
    - `USER_STATUS_CHANGED = "presence.user.status_changed"` at `presenceEvents.ts:4`
    - `USER_STOP_TYPING = "presence.room.stop_typing"` at `presenceEvents.ts:11`
    - `ROOM_ONLINE_USERS = "presence.room.online_users"` at `presenceEvents.ts:13`
- Why deprecated / legacy / outdated:
  - Backend canonical values are hyphenated, for example `presence.user.status-changed`, `presence.room.stop-typing`, and `presence.room.online-users`.
  - Frontend still switches on deprecated underscored values, while backend publishers and registries emit canonical values.
- Replace with:
  - Update frontend constants, tests, and socket handlers to canonical hyphenated values.
  - Remove backend alias maps and legacy registry fallbacks only after frontend and any external clients are migrated.
- Can remove now?
  - No. Remove after frontend is canonical and integration tests prove no underscored event type is required.

#### Redis deprecated message API remains on the active production path

- Exact file/class:
  - Deprecated API and implementations:
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/api/IRedisMessage.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/api/IRedisPublisher.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/api/IRedisSubscriber.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/message/RedisMessage.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/message/AbstractRedisMessage.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisPublisher.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/IRedisMessageRegistry.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisMessageRegistry.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/IRedisMessageSerializer.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisMessageSerializer.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/dispatcher/RedisMessageDispatcher.java`
    - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/listener/DefaultRedisMessageListener.java`
  - Canonical APIs that still depend on deprecated APIs:
    - `RedisEventPublisher.java:4,11` imports and publishes `IRedisMessage`
    - `RedisEventListener.java:16-37` extends `DefaultRedisMessageListener` and casts to `IRedisMessageSerializer`
    - `RedisAutoConfiguration.java:3,6,13,15,35,51,66,79,85-89` wires deprecated registry, serializer, publisher, dispatcher, and listener adapters
  - Active service usage:
    - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/RedisMessageFactory.java`
    - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java`
    - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/*RedisSubscriber.java`
    - `chatappBE/presence-service/src/main/java/com/example/presence/redis/*Subscriber.java`
    - `chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java`
- Exact deprecated item:
  - `IRedisMessage`, `RedisMessage`, `IRedisPublisher`, `IRedisSubscriber`, old serializers, old dispatcher, old listener, old publisher.
- Why deprecated / legacy / outdated:
  - The code comments explicitly say to use shared `com.example.common.event.EventEnvelope` metadata/payload contracts instead of message-shaped Redis APIs.
  - The "new" Redis interfaces are only partial facades because they still accept `IRedisMessage`.
- Replace with:
  - A real canonical Redis contract based on `EventEnvelope<?>` plus `EventMetadata`, or a non-deprecated `RedisEvent<T>` type that does not extend or accept `IRedisMessage`.
  - Rewrite publisher, subscriber, serializer, registry, dispatcher, listener, logger, and routing context around that type.
- Can remove now?
  - No. It is still compiled and used by chat and presence fanout, plus common Redis autoconfiguration and tests.

#### Kafka deprecated event API is still exposed by the common producer contract

- Exact file/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/KafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/KafkaEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/IKafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/IKafkaEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/core/DefaultKafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/AbstractKafkaEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventProducer.java`
- Exact deprecated item:
  - `KafkaEventPublisher` is deprecated at line 7.
  - `KafkaEvent` is deprecated at line 3.
  - `IKafkaEventPublisher` is deprecated at line 9.
  - `IKafkaEvent#getEventId()` is deprecated at lines 11-14.
  - `DefaultKafkaEventPublisher` is deprecated at line 16.
  - Old package bridge `com.example.common.integration.kafka.event.AbstractKafkaEvent` is deprecated at line 17.
  - `KafkaAutoConfiguration.java:39-42` still creates an `IKafkaEventPublisher` backed by `DefaultKafkaEventPublisher`.
  - `KafkaEventProducer.java:4,13` still imports and publishes `IKafkaEvent<?>`.
- Why deprecated / legacy / outdated:
  - The code intends `KafkaEventProducer` and shared `EventEnvelope` metadata to be canonical, but the canonical producer still requires the deprecated event interface.
  - Compatibility tests still assert alias behavior in `common-kafka/src/test/java/.../KafkaContractTest.java` and `chat-service/src/test/java/.../RealtimeMessagingAlignmentTest.java`.
- Replace with:
  - Move producer, dispatcher, registry, logger, and event wrappers to `EventEnvelope<?>` or a non-deprecated Kafka event contract.
  - Replace old package bridge imports with `com.example.common.kafka.event.AbstractKafkaEvent` where wrappers still exist.
- Can remove now?
  - No. Remove after producer interfaces and event wrappers no longer mention deprecated `IKafkaEvent`.

### High

#### WebSocket envelope contracts are duplicated and still accept legacy `data`

- Exact file/class:
  - `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java`
  - `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/protocol/RealtimeWsEvent.java`
  - `chatappFE/src/websocket/notification.socket.ts`
  - `chatappFE/src/websocket/friendship.socket.ts`
  - `chatappFE/src/websocket/presence.socket.ts`
- Exact deprecated / legacy item:
  - `WsOutgoingMessage.java:18` and `RealtimeWsEvent.java:17` use `@JsonAlias("data")` for legacy clients.
  - Both backend DTOs represent the same `{ type, payload }` envelope.
  - Notification frontend exposes `{ type, data }` at `notification.socket.ts:21-23` and maps `raw.payload` into `data` at lines 106-113.
  - Friendship frontend exposes `data` at `friendship.socket.ts:14` and maps `msg.payload ?? {}` into `data` at line 183.
  - Presence frontend already expects `payload` at `presence.socket.ts:14`.
- Why deprecated / legacy / outdated:
  - Backend sends a payload envelope but keeps a compatibility alias for old `data` clients.
  - Frontend modules disagree on the event envelope shape.
- Replace with:
  - One backend class, preferably `RealtimeWsEvent`, with `payload` only.
  - Frontend event types should all use `{ type, payload }`.
- Can remove now?
  - No. First migrate notification and friendship frontend stores/tests away from `data`, then remove `WsOutgoingMessage` or merge it into `RealtimeWsEvent`, then remove `@JsonAlias("data")`.

#### CORS config uses old and new namespaces at the same time

- Exact file/class:
  - `chatappBE/common/common-web/src/main/java/com/example/common/web/cors/CorsProperties.java`
  - Service YAML files under auth, chat, user, presence, friendship, notification, and upload.
  - `chatappBE/gateway-service/src/main/resources/application.yaml`
- Exact deprecated / legacy item:
  - Canonical class binding is `@ConfigurationProperties("common.web")` at `CorsProperties.java:17`.
  - Legacy fallback prefix is `common.security.cors` at `CorsProperties.java:20,31`.
  - Tests explicitly cover legacy fallback in `common-web/src/test/java/com/example/common/web/cors/CorsPropertiesTest.java:17-31`.
  - Gateway uses `common.web.cors`; most servlet services still use `common.security.cors`.
- Why deprecated / legacy / outdated:
  - CORS is a web concern, not a security module property. Keeping both names forces the common web module to carry a compatibility binder.
- Replace with:
  - Move all service YAML and env examples to `common.web.cors`.
  - Delete `LEGACY_CORS_PREFIX` and the fallback binder.
- Can remove now?
  - No. Service configs still depend on `common.security.cors`.

#### Gateway route config preserves both canonical `/api/v1/**` and legacy `/api/**` routes

- Exact file/class:
  - `chatappBE/gateway-service/src/main/resources/application.yaml`
- Exact deprecated / legacy item:
  - Auth route includes `/api/v1/auth/**,/api/auth/**` at line 43 and rewrites old `/api/auth/**` at line 45.
  - Users route includes `/api/v1/users/**,/api/users/**` at lines 54-56.
  - Chat route includes `/api/v1/chat/**,/api/chat/**,/api/v1/rooms/**,/api/rooms/**,/api/v1/messages/**,/api/messages/**` at lines 70-74.
  - Presence, friendship, notifications, and upload repeat the same dual route pattern at lines 93-158.
- Why deprecated / legacy / outdated:
  - `/api/v1/**` is the frontend and documentation default (`chatappFE/.env.example:6`, `chatappFE/.env.production.example:4`, `chatappFE/src/config/api.config.ts:14`).
  - Old `/api/**` aliases increase gateway route complexity and make contract ownership unclear.
- Replace with:
  - Keep only `/api/v1/**` once external consumers are confirmed migrated.
- Can remove now?
  - Not until API consumers are verified. Frontend is already on `/api/v1`, so this is likely safe after access-log confirmation.

#### Gateway configuration mixes old and new property trees

- Exact file/class:
  - `chatappBE/gateway-service/src/main/resources/application.yaml`
  - `chatappBE/gateway-service/src/test/java/com/example/gateway/health/GatewayReadinessIntegrationTest.java`
- Exact deprecated / legacy item:
  - Application YAML uses `spring.cloud.gateway.server.webflux.routes` and `spring.cloud.gateway.server.webflux.default-filters`, but `httpclient` is under `spring.cloud.gateway.httpclient` at lines 23-25.
  - `GatewayReadinessIntegrationTest.java:69-74` still uses `spring.cloud.gateway.routes[...]` test properties instead of `spring.cloud.gateway.server.webflux.routes[...]`.
- Why deprecated / legacy / outdated:
  - Spring Cloud Gateway Server WebFlux current property namespace is `spring.cloud.gateway.server.webflux.*`; keeping older `spring.cloud.gateway.*` fragments in the same module is version fragmentation.
- Replace with:
  - Move HTTP client properties and tests to the `spring.cloud.gateway.server.webflux.*` tree consistently.
- Can remove now?
  - Yes for the test property names after confirming the test still binds. For application `httpclient`, migrate in one gateway-only change and verify route health tests.

#### Notification Kafka consumer group is stale and a no-op legacy consumer still subscribes to message events

- Exact file/class:
  - `chatappBE/notification-service/src/main/resources/application.yaml`
  - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java`
- Exact deprecated / legacy item:
  - `application.yaml:46` sets `spring.kafka.consumer.group-id: user-service`.
  - `ChatMessageEventConsumer.java:15-18` listens to `KafkaTopics.CHAT_MESSAGE_SENT` and logs that it is ignoring the legacy path.
  - `AccountCreatedEventConsumer.java:18` and `FriendRequestEventConsumer.java:23` do not override `groupId`, so they inherit the stale default.
  - `MessageCreatedEventConsumer.java:33` and `ReactionEventConsumer.java:24` explicitly use `groupId = "notification-service"`.
- Why deprecated / legacy / outdated:
  - A notification service consumer group named `user-service` is a copied legacy value and can cause operational confusion.
  - A no-op listener still joins Kafka consumption for a topic it intentionally ignores.
- Replace with:
  - Set default group to `notification-service`.
  - Delete `ChatMessageEventConsumer`.
  - Either use default group consistently or specify group IDs on every listener.
- Can remove now?
  - `ChatMessageEventConsumer` can be removed now with low runtime risk because it has no behavior. Group-id cleanup should be done immediately with Kafka listener tests.

#### Upload architecture is split between upload-service and direct Cloudinary services

- Exact file/class:
  - `chatappBE/upload-service/src/main/java/com/example/upload/service/UploadSigningService.java`
  - `chatappBE/user-service/src/main/java/com/example/user/service/impl/CloudinaryService.java`
  - `chatappBE/user-service/src/main/java/com/example/user/configuration/CloudinaryConfig.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/CloudinaryService.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/CloudinaryConfig.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java`
  - `chatappFE/src/api/user.service.ts`
  - `chatappFE/src/api/room.service.ts`
- Exact deprecated / legacy item:
  - User frontend already uses upload-service signed flow at `user.service.ts:114-133`.
  - User-service still has direct `CloudinaryService` and `CloudinaryConfig`.
  - Chat room avatar still uses direct multipart `/rooms/{roomId}/avatar` at `RoomController.java:274-285` and direct `CloudinaryService` at `RoomService.java:438-450`.
  - Frontend still calls `uploadRoomAvatarApi` at `room.service.ts:98-107` and `RoomSettingsModal.tsx:139`.
- Why deprecated / legacy / outdated:
  - Upload-service is the newer architecture, but user/chat still keep older direct Cloudinary implementations.
  - Storage policy, signing, validation, and Cloudinary dependencies are duplicated.
- Replace with:
  - Use upload-service signed flow for room avatars as well.
  - Keep only upload-service Cloudinary integration.
- Can remove now?
  - User-service direct upload service appears removable after compile/test confirmation.
  - Chat-service direct upload cannot be removed until room avatar frontend/backend flow is migrated.

#### User-service depends directly on upload-service for a shared contract

- Exact file/class:
  - `chatappBE/user-service/build.gradle:31`
  - `chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java`
  - `chatappBE/upload-service/src/main/java/com/example/upload/contract/UploadAssetMetadata.java`
- Exact deprecated / legacy item:
  - `implementation project(':upload-service')` in user-service.
  - `UserProfileService.java:3` imports `com.example.upload.contract.UploadAssetMetadata`.
- Why deprecated / legacy / outdated:
  - A service module compiles against another service module just to reuse a DTO. This blocks independent service boundaries and makes the upload contract hard to version.
- Replace with:
  - Move `UploadAssetMetadata` to a shared common module, likely revived `common-media` or `common-events`, then depend on that from upload-service and user-service.
- Can remove now?
  - No. Move the contract first.

### Medium

#### Realtime contract version aliases keep Redis versions in the wrong module

- Exact file/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/realtime/RealtimeContractVersions.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/config/RedisContractVersions.java`
- Exact deprecated item:
  - `RealtimeContractVersions.CHAT_REDIS_FANOUT` at lines 21-23.
  - `RealtimeContractVersions.NOTIFICATION_REDIS_FANOUT` at lines 25-27.
  - `RealtimeContractVersions.PRESENCE_REDIS_FANOUT` at lines 29-31.
- Why deprecated / legacy / outdated:
  - Redis transport contract versions now belong in `common.redis.config.RedisContractVersions`, not `common-events`.
  - Tests still reference the deprecated aliases in chat, presence, and notification contract baseline tests.
- Replace with:
  - Update tests and any downstream imports to `RedisContractVersions`.
- Can remove now?
  - Not before tests are updated.

#### Realtime prefix constants are deprecated transport leakage

- Exact file/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/contract/RealtimeContractConventions.java`
- Exact deprecated item:
  - `CHANNEL_PREFIX_REALTIME` at lines 19-23.
  - `CHANNEL_PREFIX_WS` at lines 26-30.
- Why deprecated / legacy / outdated:
  - Transport-specific channel prefixes should not live in the shared event contract module.
- Replace with:
  - Transport-specific constants in Redis/WebSocket modules, or remove entirely if unused.
- Can remove now?
  - Likely yes after compile check. Active search found no production usage outside the class.

#### Kafka producer and consumer JSON configuration is inconsistent

- Exact file/class:
  - `chatappBE/user-service/src/main/resources/application.yaml:42,48`
  - `chatappBE/chat-service/src/main/resources/application.yaml:17,23`
  - `chatappBE/friendship-service/src/main/resources/application.yaml:41`
  - `chatappBE/notification-service/src/main/resources/application.yaml:51,56`
  - `chatappBE/auth-service/src/main/resources/application.yaml`
  - `chatappBE/presence-service/src/main/resources/application.yaml`
- Exact legacy / fragmented item:
  - User/chat/friendship use `spring.json.trusted.packages: "*"` while notification uses `"com.example.common.kafka.*"`.
  - User/chat/notification set `spring.json.add.type.headers: true`; auth/friendship/presence do not show the same producer property.
- Why deprecated / legacy / outdated:
  - Wildcard trusted packages are a broad deserialization policy.
  - Mixed type-header policy makes event contracts and consumer behavior harder to reason about.
- Replace with:
  - One Kafka serialization policy in common Kafka config: narrow trusted packages and consistent type-header behavior.
- Can remove now?
  - Needs migration/testing because listener method signatures and wrapper types depend on current deserialization behavior.

#### Jackson/ObjectMapper configuration is repeated and inconsistent

- Exact file/class:
  - `auth-service/src/main/java/com/example/auth/kafka/KafkaConfiguration.java:12-16`
  - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaConfiguration.java:12-16`
  - `user-service/src/main/java/com/example/user/kafka/KafkaConfiguration.java:12-16`
  - `notification-service/src/main/java/com/example/notification/kafka/KafkaConsumerConfig.java:27-28`
  - `friendship-service/src/main/java/com/example/friendship/configuration/JacksonConfig.java:11-12`
  - `presence-service/src/main/java/com/example/presence/configuration/JacksonConfig.java:13-17`
  - `notification-service/src/main/resources/application.yaml`
- Exact legacy / fragmented item:
  - Some services build `JsonMapper.builder().findAndAddModules()`.
  - Presence manually creates `new ObjectMapper()` and registers `JavaTimeModule`.
  - Notification uses a mix of Java bean and `spring.jackson.serialization.write-dates-as-timestamps: false`.
- Why deprecated / legacy / outdated:
  - Date/time/event serialization can differ by service.
  - Tests frequently instantiate raw `new ObjectMapper()`, which can hide production mapper differences.
- Replace with:
  - A single shared Jackson customization, preferably a common `Jackson2ObjectMapperBuilderCustomizer` or one common ObjectMapper module imported by services.
- Can remove now?
  - No. Centralize first, then delete local mapper beans.

#### Redis cache serializers are fragmented across services

- Exact file/class:
  - `chatappBE/user-service/src/main/java/com/example/user/configuration/RedisCacheConfig.java:36`
  - `chatappBE/presence-service/src/main/java/com/example/presence/configuration/RedisCacheConfig.java:32`
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/RedisCacheConfig.java:35`
  - `chatappBE/common/common-redis-cache/src/main/java/.../TimeRedisCacheManager.java`
- Exact legacy / fragmented item:
  - User and presence use `RedisSerializer.json()`.
  - Chat uses `GenericJackson2JsonRedisSerializer(objectMapper)`.
  - A common Redis cache module exists but services still define local cache configs.
- Why deprecated / legacy / outdated:
  - Cache payloads and typing behavior can diverge between services.
  - Local configs duplicate the purpose of `common-redis-cache`.
- Replace with:
  - One common cache serializer policy and cache manager module.
- Can remove now?
  - No. Align serialization first, because existing Redis cache contents may not be cross-compatible.

#### Room notification settings keep a legacy `mutedAt` fallback

- Exact file/class:
  - `chatappBE/notification-service/src/main/java/com/example/notification/entity/RoomMuteSetting.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/service/impl/RoomMuteSettingService.java`
- Exact legacy item:
  - `RoomMuteSetting.mode` at line 29 and `mutedAt` at line 31 coexist.
  - `RoomMuteSettingService.resolveMode()` treats null `mode` as muted for legacy rows at lines 62-73.
- Why deprecated / legacy / outdated:
  - The newer contract is `RoomNotificationMode`; `mutedAt` is now compatibility metadata.
- Replace with:
  - Data migration that backfills `mode` for old rows, then remove fallback behavior if `mutedAt` is not otherwise needed.
- Can remove now?
  - No. Requires DB migration and verification of existing rows.

#### Upload purpose deserializer accepts old compact aliases

- Exact file/class:
  - `chatappBE/upload-service/src/main/java/com/example/upload/dto/UploadPurposeDeserializer.java`
  - `chatappBE/upload-service/src/main/java/com/example/upload/domain/UploadPurpose.java`
- Exact legacy item:
  - Canonical enum values are `chat-attachment` and `user-avatar`.
  - Deserializer still accepts `avatar`, `useravatar`, `attachment`, and `chatattachment` at lines 33-37.
- Why deprecated / legacy / outdated:
  - Frontend already uses canonical strings at `chatappFE/src/api/user.service.ts:114-117` and `chatappFE/src/api/upload.service.ts:117-121`.
- Replace with:
  - Use enum value parsing only for canonical hyphenated values.
- Can remove now?
  - Probably yes after checking external clients and tests; internal frontend already sends canonical values.

#### Auth JWT library and API style are older than the rest of the platform

- Exact file/class:
  - `chatappBE/auth-service/build.gradle:52-54`
  - `chatappBE/auth-service/src/main/java/com/example/auth/service/impl/TokenService.java`
  - `chatappBE/auth-service/src/main/java/com/example/auth/jwt/impl/JwtVerifierService.java`
- Exact outdated item:
  - JJWT `0.11.5`.
  - `SignatureAlgorithm.RS256` at `TokenService.java:11,45`.
  - `Jwts.parserBuilder()` and `SigningKeyResolverAdapter` at `JwtVerifierService.java:34-36`.
- Why deprecated / legacy / outdated:
  - JJWT `0.11.x` is older than current JJWT `0.12+` API style.
  - Other services rely on Spring Security resource-server / Nimbus decoding, so auth has a separate JWT implementation style.
- Replace with:
  - Upgrade JJWT and migrate to current builder/parser API, or standardize more JWT validation/metadata on Spring Security resource-server patterns.
- Can remove now?
  - No. Upgrade in a focused auth change with token issuance and verification tests.

#### Room invite route has canonical and legacy meanings mixed

- Exact file/class:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java`
  - `chatappFE/src/api/room.service.ts`
- Exact legacy item:
  - Canonical join route exists at `RoomController.java:68` as `/{roomId}/join`.
  - Legacy invite route exists at `RoomController.java:77-78` as `/{roomId}/invite`, named `joinByLegacyInviteRoute`.
  - Frontend `joinRoomByInviteApi` uses `/rooms/{roomId}/join` at `room.service.ts:67`.
  - Frontend `inviteMemberApi` still uses `/rooms/{roomId}/invite` at `room.service.ts:246-252`.
- Why deprecated / legacy / outdated:
  - The same `/invite` path name is both a legacy join alias and an active invite-member endpoint pattern.
- Replace with:
  - Keep join on `/join`.
  - Rename active member invite to an explicit route such as `/members/invite`, then remove the legacy `/invite` join alias.
- Can remove now?
  - No. The frontend still uses `/invite` for member invitations, and tests assert the route exists.

### Low

#### Frontend notification API keeps backward-compatible mute wrappers

- Exact file/class:
  - `chatappFE/src/api/notification.service.ts`
- Exact legacy item:
  - `muteRoomApi` and `unmuteRoomApi` at lines 123-130.
- Why deprecated / legacy / outdated:
  - The canonical API is `updateRoomNotificationModeApi(roomId, mode)`.
- Replace with:
  - Direct callers should use `updateRoomNotificationModeApi`.
- Can remove now?
  - Likely yes after test/import check. Current search found no main-source callers.

#### Frontend notification models normalize old response fields

- Exact file/class:
  - `chatappFE/src/api/notification.service.ts`
  - `chatappFE/src/store/notification.store.tsx`
- Exact legacy item:
  - API normalizes `read` to `isRead` at `notification.service.ts:15-28`.
  - Store has backward compatibility for missing `actionRequired` at `notification.store.tsx:123-161`.
- Why deprecated / legacy / outdated:
  - Backend contract should consistently send `isRead` and `actionRequired`.
- Replace with:
  - Remove normalization after backend responses and persisted client data are verified canonical.
- Can remove now?
  - Not until client persistence and backend contract are confirmed.

#### Frontend draft migration is still shipped

- Exact file/class:
  - `chatappFE/src/utils/draftMigration.ts`
  - `chatappFE/src/store/draft.store.tsx`
- Exact legacy item:
  - `migrateLegacyDraft`, `isLegacyDraft`, and `migrateDraftIfNeeded`.
  - Store API `initializeDraftWithMigration` at `draft.store.tsx:19,55-57`.
- Why deprecated / legacy / outdated:
  - It supports an old `{ text, attachments }` local-storage format after moving to blocks.
- Replace with:
  - Blocks-only draft initialization after a storage-version migration window.
- Can remove now?
  - Only if old local drafts can be intentionally dropped.

#### Frontend room notification local-storage migration is still shipped

- Exact file/class:
  - `chatappFE/src/store/room.store.tsx`
  - `chatappFE/src/store/notification.store.tsx`
  - `chatappFE/src/utils/notificationModePolicy.ts`
- Exact legacy item:
  - `LEGACY_ROOM_MUTE_STORAGE_KEY = "notification_mutes_by_room"` at `room.store.tsx:33` and `notification.store.tsx:57`.
  - Legacy boolean parsing at `room.store.tsx:56-62` and `notification.store.tsx:278-287`.
  - `legacyIsMuted` policy fallback at `notificationModePolicy.ts:5-11`.
- Why deprecated / legacy / outdated:
  - New contract is room notification mode, not boolean mute.
- Replace with:
  - Mode-only local storage.
- Can remove now?
  - Only after deciding old browser storage may be discarded or after writing a one-time storage migration.

#### Frontend feature flags preserve old unread/sidebar behavior

- Exact file/class:
  - `chatappFE/src/config/featureFlags.ts`
- Exact legacy item:
  - `enableSelfMessageUnreadExclusion` comment says false preserves legacy unread behavior at lines 15-19.
  - Sidebar room notification manager flag comment says false preserves legacy behavior at line 30.
- Why deprecated / legacy / outdated:
  - These flags keep old behavior branches alive after the new behavior is default.
- Replace with:
  - Remove flags once defaults are stable in production.
- Can remove now?
  - Not without confirming rollout state.

## 3. Version Fragmentation

- Gradle wrapper versions are split:
  - Root `chatappBE/gradle/wrapper/gradle-wrapper.properties:3` uses Gradle `8.14`.
  - Service wrappers under auth/chat/user/friendship/presence/notification use Gradle `9.2.1`.
  - Gateway and upload rely on root/no local wrapper style.
- Gradle settings are fragmented:
  - Root `chatappBE/settings.gradle` includes all modules.
  - Auth/chat/user/presence/notification service settings files still say `rootProject.name = 'demo'`.
  - Friendship has a partial standalone settings file with only a subset of common modules.
  - Gateway has its own plugin management and `rootProject.name = 'gateway-service'`.
- Dependency version style is mixed:
  - Most service `build.gradle` files repeat Spring Boot `3.5.6` and dependency-management `1.1.7`.
  - Gateway uses root plugin versions.
  - Common modules import the Boot BOM separately.
  - `springCloudVersion = '2025.0.1'` is repeated in chat, friendship, gateway, and common-feign.
  - `common-feign/build.gradle:32` hard-pins `spring-cloud-starter-openfeign:4.3.1` while chat/friendship use the BOM-managed dependency.
- Spring Cloud Gateway config style is mixed:
  - Production YAML is mostly `spring.cloud.gateway.server.webflux.*`.
  - HTTP client config and one readiness test still use old `spring.cloud.gateway.*` paths.
- CORS config style is mixed:
  - Gateway uses `common.web.cors`.
  - Most servlet services use `common.security.cors`.
- Kafka style is mixed:
  - Notification has a stale default consumer group and some explicit listener groups.
  - Trusted packages and type header settings differ across services.
- Redis style is mixed:
  - Common Redis autoconfig exists, but chat, presence, and notification still define manual listener containers.
  - Redis event APIs are "new" by name but still wrap deprecated `IRedisMessage`.
  - Cache serialization differs between services.
- Jackson style is mixed:
  - Multiple service-local `ObjectMapper` beans and raw test mappers exist.
  - Presence manually registers Java time support; other services use `JsonMapper.findAndAddModules()`.
- WebSocket contract style is mixed:
  - Backend has both `WsOutgoingMessage` and `RealtimeWsEvent`.
  - Frontend chat/presence use `payload`; notification/friendship use `data`.
- REST route style is mixed:
  - Gateway keeps `/api/v1/**` plus old `/api/**` aliases.
  - Room controller has `/join` plus a legacy `/invite` join alias, while frontend still uses `/invite` for member invitation.
- Upload style is mixed:
  - Upload-service provides signed upload.
  - User-service and chat-service still have direct Cloudinary integration.
  - User-service depends directly on upload-service for a DTO.
- Frontend persistence/contracts are mixed:
  - Notification mode vs old boolean mute.
  - `isRead` vs old `read`.
  - Blocks drafts vs old `{ text, attachments }`.

## 4. Safe Removals Now

- Remove `socket.io-client` from `chatappFE/package.json:35` and lockfile entries, after running frontend install/test. No source imports were found; the app uses native `WebSocket`.
- Remove `spring-boot-starter-websocket` from `chatappBE/auth-service/build.gradle:40` if a compile/test run confirms no auth WebSocket beans. No auth-service WebSocket code was found.
- Remove `ChatMessageEventConsumer` in `notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java`. It only logs that the legacy path is ignored.
- Change notification default Kafka group from `user-service` to `notification-service` in `notification-service/src/main/resources/application.yaml:46`, then remove redundant explicit group IDs if desired.
- Remove or update `RealtimeContractConventions.CHANNEL_PREFIX_REALTIME` and `CHANNEL_PREFIX_WS` after compile check; active search found no production usage.
- Remove frontend `muteRoomApi` / `unmuteRoomApi` wrappers in `chatappFE/src/api/notification.service.ts:123-130` if tests/imports confirm no consumers.
- Remove stale `common/common-media` module files or reintroduce it intentionally as the shared upload contract module. It is not included in root `settings.gradle`, and its only useful contract has moved under upload-service.
- Fix stale Gradle descriptions:
  - `common/common-websocket/build.gradle:8` says `Common Redis Library`.
  - `common/common-redis-cache/build.gradle:8` also says `Common Redis Library`.
- Clean mojibake comments in Docker/deploy files where present. This is safe text cleanup but not functionally urgent.

## 5. Requires Migration First

### Redis message API removal

- What depends on it:
  - Common Redis publisher, subscriber, serializer, registry, dispatcher, listener, logger, routing context, autoconfiguration.
  - Chat Redis publisher/subscribers and tests.
  - Presence Redis publisher/subscribers and tests.
- What needs to change first:
  - Introduce a non-deprecated Redis event contract that does not accept `IRedisMessage`.
  - Rewrite common Redis components around that contract.
  - Migrate chat and presence publishers/subscribers.
- Suggested migration order:
  1. Add canonical `RedisEvent` or use `EventEnvelope<?>`.
  2. Add serializer/registry/dispatcher/listener for canonical type.
  3. Convert chat fanout.
  4. Convert presence fanout.
  5. Convert tests.
  6. Delete deprecated Redis API package and adapters.

### Kafka deprecated event API removal

- What depends on it:
  - `KafkaEventProducer`, `KafkaAutoConfiguration`, event wrappers, common Kafka tests, chat realtime alignment tests.
- What needs to change first:
  - Make producer APIs accept `EventEnvelope<?>` or a non-deprecated Kafka event type.
  - Remove `IKafkaEventPublisher` bean creation.
  - Replace deprecated old package bridge imports.
- Suggested migration order:
  1. Define canonical producer signature.
  2. Convert event factories/wrappers.
  3. Convert consumers/tests.
  4. Remove aliases and `DefaultKafkaEventPublisher`.

### Presence event alias removal

- What depends on it:
  - Frontend constants and socket switch handling.
  - Common Kafka/Redis registry fallback logic.
  - Tests that assert old underscore compatibility.
- What needs to change first:
  - Frontend must use canonical hyphenated values.
  - Contract tests must reject underscored values.
- Suggested migration order:
  1. Update frontend constants/tests.
  2. Verify end-to-end presence events.
  3. Remove backend alias maps and registry fallback warnings.

### WebSocket `data` alias removal

- What depends on it:
  - Notification socket/store tests and friendship socket/store tests.
  - Backend compatibility DTO tests.
- What needs to change first:
  - Frontend notification/friendship should use `payload`.
  - Backend should keep only one envelope class.
- Suggested migration order:
  1. Convert frontend event types and stores to `payload`.
  2. Delete `WsOutgoingMessage` or merge it into `RealtimeWsEvent`.
  3. Remove `@JsonAlias("data")`.

### CORS legacy namespace removal

- What depends on it:
  - Auth, chat, user, presence, friendship, notification, and upload YAML configs.
  - `CorsPropertiesTest` legacy fallback coverage.
- What needs to change first:
  - Rename service config from `common.security.cors` to `common.web.cors`.
- Suggested migration order:
  1. Update YAML/env examples.
  2. Remove fallback binder.
  3. Update tests to canonical-only behavior.

### Gateway legacy route removal

- What depends on it:
  - Any clients still calling `/api/**` without `/v1`.
  - Gateway rewrite rules.
- What needs to change first:
  - Confirm from access logs or client inventory that `/api/**` aliases are unused.
- Suggested migration order:
  1. Add temporary logging/metrics for legacy route hits.
  2. Remove old `/api/**` route predicates and rewrites.
  3. Keep `/api/v1/**` only.

### Upload architecture cleanup

- What depends on it:
  - Chat room avatar upload still uses direct multipart Cloudinary flow.
  - User-service imports `UploadAssetMetadata` from upload-service.
  - User/chat Cloudinary configs and dependencies.
- What needs to change first:
  - Move upload DTO contract to a common module.
  - Convert room avatar upload to upload-service signed flow.
- Suggested migration order:
  1. Move `UploadAssetMetadata` to a shared module.
  2. Make upload-service and user-service depend on the shared module.
  3. Remove user-service direct Cloudinary upload service if unused.
  4. Migrate room avatar frontend/backend to upload-service.
  5. Remove chat-service direct Cloudinary integration.

### Room mute legacy row fallback

- What depends on it:
  - Existing database rows with null `mode` and non-null `mutedAt`.
- What needs to change first:
  - Migration to set `mode = NOTHING` for legacy muted rows and `mode = NO_RESTRICT` or row deletion for unmuted rows.
- Suggested migration order:
  1. Add DB migration.
  2. Verify all rows have non-null `mode`.
  3. Remove null fallback.

### Frontend persistence migrations

- What depends on it:
  - Browser localStorage drafts and room notification mute settings.
- What needs to change first:
  - Decide whether old local data can be discarded or must be migrated one time.
- Suggested migration order:
  1. Add storage version marker.
  2. Run one-time migration.
  3. Remove legacy parsing and helper APIs.

## 6. Dead Code / Legacy Compatibility Layer

- Kafka compatibility layer:
  - `KafkaEventPublisher`, `KafkaEvent`, `IKafkaEventPublisher`, deprecated `IKafkaEvent#getEventId()`, `DefaultKafkaEventPublisher`, and old package `com.example.common.integration.kafka.event.AbstractKafkaEvent`.
  - Not dead yet because common producer/autoconfiguration and tests still use it.
- Redis compatibility layer:
  - `IRedisMessage`, `IRedisPublisher`, `IRedisSubscriber`, `RedisMessage`, `AbstractRedisMessage`, old registry/serializer/dispatcher/listener/publisher types.
  - Not dead yet because chat/presence/common Redis still use it heavily.
- Presence alias layer:
  - `PresenceEventType.DEPRECATED_ALIASES`, `LEGACY_BY_NORMALIZED`, `normalize`, `legacyAliasOf`, registry fallback code in common Kafka/Redis.
  - Keep temporarily until frontend constants are canonical.
- WebSocket compatibility layer:
  - `WsOutgoingMessage` duplicates `RealtimeWsEvent`.
  - `@JsonAlias("data")` supports old clients.
  - Notification/friendship frontend still expose `data`.
- Gateway compatibility layer:
  - `/api/**` aliases and `RewritePath` rules normalize legacy routes into `/api/v1/**`.
- Upload compatibility layer:
  - User-service and chat-service direct Cloudinary upload classes coexist with upload-service.
  - `UploadPurposeDeserializer` accepts compact aliases.
- Notification compatibility layer:
  - `ChatMessageEventConsumer` is a dead no-op legacy Kafka path.
  - `RoomMuteSettingService` legacy `mutedAt` fallback.
  - Frontend `read` to `isRead` normalization and missing `actionRequired` fallback.
  - Frontend mute/unmute wrappers and boolean mute localStorage fallback.
- Draft compatibility layer:
  - `draftMigration.ts` and `initializeDraftWithMigration`.
- Stale module/config:
  - `common/common-media` exists as a module directory but is not included by root settings.
  - Several standalone service settings/wrappers duplicate the root multi-project build.

## 7. Dependency / Version Cleanup

- Align Gradle wrappers:
  - Root uses Gradle `8.14`; service wrappers use `9.2.1`.
  - Pick one wrapper, preferably root-only for the multi-project build, and remove/update standalone wrappers/settings.
- Centralize plugin and BOM versions:
  - Spring Boot `3.5.6`, dependency-management `1.1.7`, and Spring Cloud `2025.0.1` are repeated.
  - Move versions into root build/version catalog/platform.
- Remove explicit OpenFeign version:
  - `common/common-feign/build.gradle:32` pins `spring-cloud-starter-openfeign:4.3.1` while the BOM is already imported.
- Remove unused frontend dependency:
  - `socket.io-client` in `chatappFE/package.json:35` is unused by source.
- Remove likely unused auth dependency:
  - `auth-service/build.gradle:40` includes `spring-boot-starter-websocket` with no auth WebSocket code found.
- Audit explicit Nimbus dependencies:
  - `chat-service/build.gradle:53` and `presence-service/build.gradle:41` explicitly include `com.nimbusds:nimbus-jose-jwt`; source does not show direct main-code imports in those services. Spring Security resource server normally brings Nimbus transitively.
- Upgrade/authenticate JJWT usage:
  - `auth-service` uses JJWT `0.11.5`; plan a current JJWT API migration.
- Collapse Cloudinary dependencies:
  - `cloudinary-http44:1.39.0` exists in user, chat, and upload.
  - Keep it only in upload-service after avatar flows are migrated.
- Fix Kafka/Docker version direction:
  - Compose uses Zookeeper-based Kafka images (`cp-zookeeper:7.6.0`, `cp-kafka:7.6.0`) and `KAFKA_ZOOKEEPER_CONNECT`.
  - Plan migration to KRaft-based local infrastructure when convenient; this is infrastructure legacy, not an immediate app code break.
- Normalize env/config variable names:
  - Auth uses `KAFKA_SERVERS`; other services use `SPRING_KAFKA_BOOTSTRAP_SERVERS`.
  - Redis uses both `REDIS_HOST` and `SPRING_DATA_REDIS_HOST`.
  - User service URLs use `USER_SERVICE_BASE_URL`, `USER_SERVICE_URL`, and `SERVICES_USER_URL`.
- Remove stale common module or make it real:
  - `common-media` is not included in root settings but is the right place for upload DTOs if restored.

## 8. Recommended Cleanup Order

1. Remove immediate safe dead code and dependencies:
   - `socket.io-client`
   - auth websocket starter
   - notification `ChatMessageEventConsumer`
   - stale common module descriptions
2. Fix notification Kafka configuration:
   - default group `notification-service`
   - consistent group IDs
   - standard trusted packages/type-header policy
3. Normalize build/version management:
   - one Gradle wrapper strategy
   - one plugin/BOM version source
   - remove explicit OpenFeign version
4. Migrate CORS config to `common.web.cors` everywhere and delete fallback binder.
5. Migrate frontend presence event constants to canonical hyphenated names.
6. Collapse WebSocket envelope shape:
   - frontend `payload` everywhere
   - one backend envelope class
   - remove `@JsonAlias("data")`
7. Move upload shared DTOs to a common module and remove user-service dependency on upload-service.
8. Remove user-service direct Cloudinary upload path if compile/tests confirm it is no longer used.
9. Migrate chat room avatar upload to upload-service signed flow, then remove chat direct Cloudinary integration.
10. Replace Redis deprecated message APIs with canonical event envelopes.
11. Replace Kafka deprecated APIs with canonical event envelopes.
12. Remove gateway `/api/**` aliases after access-log confirmation.
13. Remove frontend localStorage compatibility branches after a storage-version migration window.
14. Upgrade auth JJWT usage in a focused auth/security change.
15. Plan Docker Kafka KRaft migration and env var normalization.

## 9. Final Verdict

- What can be removed now:
  - Frontend `socket.io-client`.
  - Auth-service websocket starter if compile confirms no hidden use.
  - Notification no-op `ChatMessageEventConsumer`.
  - Deprecated realtime prefix constants after compile check.
  - Frontend mute/unmute API wrappers if import check remains clean.
  - Stale `common-media` directory only if not reused for upload contracts.

- What must be migrated first:
  - Redis deprecated message API.
  - Kafka deprecated event API.
  - Presence underscored event aliases.
  - WebSocket `data` alias and duplicate envelope classes.
  - CORS legacy `common.security.cors` namespace.
  - Gateway legacy `/api/**` routes.
  - Upload direct Cloudinary paths and user-service dependency on upload-service.
  - Room notification `mutedAt` fallback.
  - Frontend draft/mute/read compatibility branches.

- What should stay temporarily:
  - Presence alias maps until frontend and external clients use hyphenated event names.
  - Redis/Kafka compatibility adapters until the common event envelope migration is complete.
  - Gateway route aliases until legacy route traffic is measured.
  - LocalStorage migration helpers until product accepts dropping old client state or a versioned migration is shipped.
  - Room mute `mutedAt` fallback until DB rows are migrated.

- Highest-risk cleanup area:
  - Redis/Kafka realtime contracts. They cross common modules, chat, presence, notification, WebSocket fanout, event metadata, serialization, and tests. Removing the deprecated classes without first replacing the canonical contracts would break runtime event delivery, not just compilation.
