# Service Integration Readiness Review

Date: 2026-05-12

Verdict: **Not integration-ready**

This review is limited to backend service/app modules under `chatappBE/**` excluding `chatappBE/common/**`, with gateway, docker-compose, and application configuration inspected only where they affect inter-service flow.

## 1. Scope Reviewed

Reviewed service/application modules:

- `chatappBE/auth-service`
- `chatappBE/user-service`
- `chatappBE/chat-service`
- `chatappBE/friendship-service`
- `chatappBE/notification-service`
- `chatappBE/presence-service`
- `chatappBE/upload-service`
- `chatappBE/gateway-service`
- `chatappBE/realtime-edge-service`

Reviewed supporting integration surfaces:

- Gateway route definitions and authentication/header propagation in `gateway-service/src/main/resources/application.yaml` and gateway security/filter code.
- Docker Compose service topology where needed for service discovery and deployment flow.
- Kafka topic names, producers, listeners, and event adapter code in service modules.
- Redis publisher/subscriber paths in chat, presence, and realtime edge modules.
- Feign/internal clients used for cross-service checks.
- Upload metadata handoff surfaces for user avatars, chat attachments, and room avatars.

Validation commands run:

- `.\gradlew.bat --continue :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :friendship-service:compileJava :notification-service:compileJava :presence-service:compileJava :upload-service:compileJava :gateway-service:compileJava`
  - Failed before service compilation completed because shared messaging/websocket modules do not compile against their current APIs.
- `.\gradlew.bat :upload-service:test`
  - Passed.
- `.\gradlew.bat :gateway-service:test`
  - Failed in test compilation because `GatewayCorsIntegrationTest` still constructs `GatewayConfig` without the required `CorsProperties`.

## 2. Integration Flows Reviewed

### 2.1 Auth -> User Flow

Expected flow:

- Auth creates an account.
- Auth publishes an account-created event.
- User consumes that event and creates the user profile.

Findings:

- Auth and user both reference legacy/missing event types under `com.example.common.integration.kafka.event.*`.
- Auth and user reference `KafkaTopics.ACCOUNT_CREATED`, but the current shared Kafka topic catalog visible to services does not define that constant.
- The current shared event model appears to be based on canonical `EventEnvelope` payloads such as `AccountCreatedPayload`, while the service code still uses legacy wrapper event classes.

Readiness: **Blocked**.

### 2.2 Chat -> Notification Flow

Expected flow:

- Chat persists a message.
- Chat publishes message/reaction events.
- Notification consumes them, applies recipient preferences/dedupe, and creates notifications.
- Redis/websocket fanout carries realtime updates.

Findings:

- Chat contains competing implementations of `IMessageEventPublisher`: `ChatMessageEventPublisherAdapter` and `KafkaChatMessageEventPublisher`.
- Chat contains competing implementations of `IReactionEventPublisher`: `ChatMessageEventPublisherAdapter` and `KafkaReactionEventPublisher`.
- Injection sites use unqualified publisher interfaces, so the application context is ambiguous once the stale compile drift is corrected.
- One publisher path uses canonical envelopes; another uses missing legacy Kafka event wrappers and missing `KafkaTopics.CHAT_MESSAGE_*` constants.
- Notification has overlapping consumers for chat message events, including one legacy logging consumer and one notification-creation consumer.
- Chat publishes events after persistence as best-effort async work. Failure to publish does not fail the message send after the database write.

Readiness: **Blocked** for Kafka contract and bean ambiguity. **Risky** for notification durability even after compile alignment.

### 2.3 Friendship -> Notification Flow

Expected flow:

- Friendship publishes friend-request and friendship lifecycle events.
- Notification consumes request/accept/decline/cancel/block events and creates or resolves notifications.

Findings:

- Friendship producers/consumers reference missing legacy classes such as `FriendRequestKafkaEvent`, `FriendshipEvent`, and `FriendRequestEvent`.
- Friendship code references `KafkaTopics.FRIENDSHIP_EVENTS` and `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS`; the current visible constants are named differently (`TOPIC_FRIENDSHIP_EVENTS`, `TOPIC_FRIENDSHIP_REQUEST_EVENTS`).
- Notification friendship consumers use the same legacy event model and missing topic constants.

Readiness: **Blocked**.

### 2.4 Chat -> Friendship Block-Check Flow

Expected flow:

- Chat asks friendship whether either side has blocked the other before allowing message send.

Findings:

- Chat calls `GET /api/v1/internal/friends/blocked-between` via the friendship Feign client.
- Friendship exposes that exact internal endpoint under `/api/v1/internal/friends`.
- Friendship security permits `/api/v1/internal/**`.
- Chat fails closed when the block-check call fails, preventing sends if the block status cannot be verified.

Readiness: **Mostly ready structurally**.

Remaining concern:

- The internal endpoint is unauthenticated at the service boundary. That may be acceptable only if network policy/service mesh rules enforce internal-only access. The application code itself does not.

### 2.5 Upload -> User/Chat Avatar Metadata Flow

Expected flow:

- Upload signs and confirms Cloudinary uploads.
- User/avatar and chat/avatar or chat attachment metadata is accepted only after policy-compliant upload metadata is supplied.

Findings:

- Upload service has a clear prepare/confirm API and passed its isolated test suite.
- User avatar update validates image type, size, format, and Cloudinary secure URL.
- Chat message attachment validation checks Cloudinary URL shape and size.
- Chat room avatars still use direct multipart upload to Cloudinary rather than the upload-service metadata flow.
- Chat attachment metadata is not clearly bound to upload-service confirmation, expected folder, upload owner, or upload purpose.
- Gateway upload routing is incorrect: it routes `/api/v1/upload/**` and `/api/upload/**`, while the upload controller exposes `/api/v1/uploads/**`.

Readiness: **Blocked at gateway** and **incomplete for chat room avatar metadata flow**.

### 2.6 Presence Flow

Expected flow:

- Presence accepts websocket connections, authenticates them, tracks user/room presence, and publishes Redis events for other interested services/edges.

Findings:

- Presence websocket and REST surfaces exist.
- Presence uses shared-looking Redis channel names for user, room, and global presence events.
- Presence code references `RedisMessage` and subscriber `onMessage` style APIs that do not match the current shared Redis publisher/subscriber contract, which is envelope-based.
- Similar Redis contract drift exists in chat and realtime edge code.

Readiness: **Blocked** by Redis contract/API mismatch.

### 2.7 Gateway Route/Auth/Header Propagation

Expected flow:

- Gateway authenticates external API requests.
- Gateway propagates identity headers while preserving `Authorization`.
- Routes map to actual downstream controller paths.

Findings:

- Gateway has a JWT filter that derives `X-User-Id` from the JWT subject.
- Spring Cloud Gateway should preserve `Authorization` unless explicitly stripped, so downstream resource-server validation can still work.
- Websocket paths are permitted at gateway level and then validated by service websocket handshake interceptors.
- Friendship public route is wrong: gateway exposes `/api/v1/friendship/**` and `/api/friendship/**`, but the friendship controller exposes `/api/v1/friends/**`.
- Upload route is wrong: gateway exposes singular `/api/v1/upload/**` and `/api/upload/**`, while upload exposes plural `/api/v1/uploads/**`.
- Gateway readiness treats any downstream HTTP status below 500 as UP, which can hide route/auth mismatches returning 401 or 404.

Readiness: **Blocked** for friendship and upload external API access.

### 2.8 Kafka/Redis Event Path Consistency

Expected flow:

- Services agree on topic/channel names, envelope format, payload classes, producer API, and listener API.

Findings:

- Kafka paths are split between legacy wrapper events and canonical envelopes.
- Several service modules import event classes that do not exist in the current source tree.
- Several services call producer methods or topic constants that do not match the current shared Kafka API.
- Redis paths are split between old `RedisMessage` semantics and current `EventEnvelope` semantics.
- `realtime-edge-service` is not included in root `settings.gradle`, is not present in docker-compose deployment, and depends on a non-included `common-event-contract` project. It is not currently part of the runnable backend integration topology.

Readiness: **Blocked**.

## 3. What Is Already Good

- The intended service boundaries are recognizable: auth owns account creation, user owns profile/avatar state, chat owns message/room state, friendship owns relationship/block state, notification owns notification persistence, upload owns signed upload policy, presence owns online/typing state, and gateway owns edge routing.
- The chat-to-friendship block check is the strongest integration path reviewed. The endpoint and Feign client line up, and chat fails closed when it cannot verify block state.
- Gateway authentication has the right general shape: edge JWT validation, `X-User-Id` propagation, and downstream services still able to validate the bearer token.
- Upload service has focused policy validation and passed its isolated tests.
- User avatar metadata validation is stricter than a raw URL setter; it checks type, format, size, and Cloudinary URL shape.
- Notification service contains useful domain behavior for recipient mode handling, dedupe, sticky notification resolution, and self-notification suppression.
- Chat message event payload intent is reasonably rich: recipient IDs, direct/group context, mentions, reply context, and sender metadata are represented in the newer adapter path.
- Redis channel naming for chat/presence/realtime intent is moving toward shared constants rather than raw scattered literals.

## 4. Integration Blockers

### B1. Backend compile/readiness check is not green

The targeted multi-service compile check fails before service compilation can complete because shared integration modules currently fail:

- Kafka serialization references `JavaTimeModule` without the required Jackson JSR310 dependency available to that module.
- Redis pub/sub adapter calls the dispatcher with a channel argument that the current dispatcher API does not accept.
- Websocket code references methods missing from the current event catalog.

This review intentionally does not redesign `common/**`, but the service layer cannot be considered integration-ready while its required integration contracts do not compile.

### B2. Kafka event contract drift blocks auth, user, chat, friendship, and notification

Services are not aligned on one Kafka contract. Current service code mixes:

- Legacy wrapper imports under `com.example.common.integration.kafka.event.*`.
- Legacy `KafkaTopics.*` constants that are absent or renamed.
- Canonical `EventEnvelope` publishing.
- Producer API calls that do not match the current shared producer method names.

Affected flows:

- Auth -> user account creation.
- Auth/user -> notification welcome/account notification.
- Chat -> notification message/reaction events.
- Friendship -> notification friend request/friendship events.

### B3. Redis event contract drift blocks chat realtime and presence integration

Chat, presence, and realtime edge code still reference an older Redis message/subscriber model, while the visible shared Redis contract is envelope-based. This blocks:

- Presence fanout.
- Chat websocket fanout.
- Realtime edge subscription path.
- Cross-service consistency of channel payloads.

### B4. Gateway friendship route does not map to the friendship controller

Gateway exposes:

- `/api/v1/friendship/**`
- `/api/friendship/**`

Friendship exposes:

- `/api/v1/friends/**`

The rewrite rule sends requests to paths that do not match the controller. External friendship APIs are not routable through the gateway as configured.

### B5. Gateway upload route does not map to the upload controller

Gateway exposes singular upload paths:

- `/api/v1/upload/**`
- `/api/upload/**`

Upload exposes:

- `/api/v1/uploads/**`

The rewrite also drops the `uploads` segment for legacy `/api/upload/**` traffic. Upload prepare/confirm is not externally routable through the gateway as configured.

### B6. Notification Kafka consumer group is configured as `user-service`

`notification-service` config sets the Kafka consumer group to `user-service`. Listeners without an explicit `groupId` will join the user-service group.

Impact:

- Notification consumers can compete with user-service consumers for account-created events.
- User profile creation or notification creation can be missed depending on partition assignment and listener startup order.
- This is especially dangerous because some notification listeners do set explicit group IDs while others rely on config, making behavior inconsistent.

### B7. Chat publisher beans are ambiguous

Chat defines multiple beans for the same event publisher interfaces and injects those interfaces without qualifiers:

- `IMessageEventPublisher`
- `IReactionEventPublisher`

Once the stale compile drift is corrected, the Spring context is likely to fail or select an unintended event path unless one implementation is removed or explicitly selected.

### B8. Realtime edge is not part of the deployable backend topology

`realtime-edge-service` exists under `chatappBE/**`, but it is not included in the root Gradle settings and is not wired into docker-compose/gateway deployment. It also depends on a non-included `common-event-contract` project.

That means realtime edge cannot be treated as part of the frozen integration topology.

## 5. Integration Risks

### R1. Chat event publication is best-effort after persistence

Message persistence can succeed while Kafka publication fails. That means notifications and realtime fanout can silently diverge from durable chat state unless an outbox, retry, or reconciliation path exists.

### R2. Internal friendship block endpoint is application-public inside the network

The block-check endpoint is permitted without authentication. This is acceptable only if infrastructure enforces internal-only reachability. Application security alone does not protect it.

### R3. Upload metadata flow is partially client-mediated

User avatar metadata is validated, but chat attachment and room avatar flows are not consistently bound to upload confirmation, purpose, owner, folder, or Cloudinary public ID policy. A client can provide Cloudinary-looking metadata that did not necessarily pass upload-service confirmation.

### R4. Websocket token-in-query flow has leakage risk

Gateway permits websocket paths and services validate query-token handshakes. That can work, but query tokens are easier to leak through logs, browser history, reverse proxies, and observability tools than header-based tokens.

### R5. Gateway readiness can mask integration breakage

The downstream readiness check treats HTTP statuses below 500 as healthy. A misrouted service returning 404 or a protected route returning 401 can still be reported as UP.

### R6. Docker/deployment topology does not match all service code

The backend contains realtime edge code that is not part of the root build/deploy path. Integration expectations around realtime delivery should be clarified: either realtime edge is part of the frozen topology, or it is explicitly excluded.

## 6. Recommended Integration Test Matrix

### Build and boot

- Root or targeted backend `compileJava` across all included services.
- Service context-load tests for auth, user, chat, friendship, notification, presence, upload, and gateway.
- Docker-compose smoke boot with Kafka, Redis, database dependencies, and all included services.

### Auth -> user

- Register account in auth.
- Assert account-created event is published on the agreed topic with the agreed envelope.
- Assert user-service consumes it and creates the user profile exactly once.
- Assert duplicate account-created event is idempotent.

### Auth/user -> notification

- Register account.
- Assert user profile creation and welcome/account notification both happen without consumer-group competition.
- Assert notification listener group is distinct from user-service.

### Chat -> notification

- Send direct message.
- Send room message.
- Send message with mentions.
- Send reply message.
- Add and remove reaction.
- Assert Kafka event payloads deserialize in notification-service.
- Assert notifications are created for expected recipients and suppressed for sender/self cases.
- Assert realtime Redis/websocket fanout matches persisted message state.

### Friendship -> notification

- Send friend request.
- Accept friend request.
- Decline friend request.
- Cancel friend request.
- Block and unblock user.
- Assert notification creation/resolution behavior for each lifecycle event.
- Assert duplicate events are idempotent.

### Chat -> friendship block check

- Unblocked pair can send.
- Blocked pair cannot send.
- Reverse-direction block also prevents send.
- Friendship service unavailable causes chat send to fail closed.
- Internal endpoint is unreachable externally or protected by infrastructure policy.

### Upload -> user/chat avatar metadata

- Prepare and confirm user avatar upload, then update user avatar metadata.
- Reject user avatar metadata with wrong resource type, format, size, or non-Cloudinary URL.
- Prepare and confirm chat attachment upload, then send message with that attachment.
- Reject chat attachment metadata that was not confirmed or does not match upload purpose/owner.
- Exercise room avatar update through the selected final flow: upload-service metadata flow or direct multipart, but not both ambiguously.

### Presence

- Connect websocket with valid token.
- Reject missing/invalid/expired token.
- Heartbeat updates presence state.
- Join and leave room presence.
- Typing state expires.
- Multi-instance presence propagation via Redis works.
- Disconnect cleanup is propagated to other subscribers.

### Gateway

- Route matrix through gateway for auth, users, rooms, messages, friends, uploads, notifications, presence, and websocket paths.
- Assert `Authorization` reaches downstream services.
- Assert `X-User-Id` is set consistently after JWT validation.
- Assert public routes are truly public and protected routes reject unauthenticated requests.
- Assert gateway readiness fails for 401/404 route mismatches, not only 5xx/downstream connection failures.

### Kafka/Redis contracts

- Contract tests per topic/channel proving exact topic/channel names.
- Producer/consumer serialization tests using the same event envelope and payload classes.
- Backward/duplicate event tests where idempotency is expected.
- Negative deserialization tests for unknown event types and invalid payload versions if versioning remains part of the contract.

## 7. Final Verdict

**The frozen service layer is not integration-ready.**

The most serious issue is not one isolated broken route or one missing listener. The service layer currently has systemic contract drift between services and their shared Kafka/Redis APIs. Multiple critical flows still reference legacy event classes, stale topic constants, or old Redis message abstractions that do not match the current available contracts. On top of that, the gateway cannot route public friendship or upload APIs to their actual controllers, notification is configured with the wrong Kafka consumer group, and chat has ambiguous event publisher beans.

The upload service passed its own focused test suite, and the chat-to-friendship block-check path is close to ready. Those are useful positives, but they do not offset the integration blockers.

Minimum bar before this layer can be frozen as integration-ready:

- Backend compile checks must pass for all included services.
- Kafka event contracts must be aligned to one producer/listener/topic/envelope model.
- Redis event contracts must be aligned to one publisher/subscriber/channel/envelope model.
- Gateway friendship and upload routes must map to actual downstream controllers.
- Notification must use its own consumer group consistently.
- Chat must have one selected publisher implementation per publisher interface.
- Realtime edge must either be included in the build/deploy topology or explicitly removed from the frozen integration scope.
