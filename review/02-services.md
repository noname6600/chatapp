# 02. Services

## Service Inventory

## auth-service
Responsibilities:
- register/login/local-password flows
- Google OAuth login + exchange
- access token (JWT RS256) issuance
- refresh token rotation/revocation
- email verification + password reset tokens

Key files:
- `auth-service/controller/AuthController.java`
- `auth-service/service/impl/AuthService.java`
- `auth-service/service/impl/TokenServiceFacade.java`
- `auth-service/jwt/impl/JwtVerifierService.java`

Boundaries:
- Owns account identity and refresh-token lifecycle.
- Emits account-created events.

Risks:
- refresh token storage is DB-backed only; no device/session metadata granularity beyond token rows.

## user-service
Responsibilities:
- profile CRUD/search/bulk profile lookup
- avatar metadata handling
- profile cache layer with redis cache manager
- consumes account-created events

Key files:
- `user-service/controller/UserProfileController.java`
- `user-service/service/impl/UserProfileService.java`
- `user-service/infrastructure/kafka/AccountCreatedConsumer.java`

Boundary rationale:
- profile model evolves independently from auth identity.

## chat-service
Responsibilities:
- room lifecycle, membership, moderation, pins
- message send/edit/delete/forward, reactions
- sequence assignment per room
- emits chat events to Redis and Kafka
- checks friendship block constraints

Key files:
- `chat-service/modules/room/controller/RoomController.java`
- `chat-service/modules/message/controller/MessageCommandController.java`
- `chat-service/modules/message/application/command/impl/MessageCommandService.java`
- `chat-service/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`

Boundary rationale:
- highest write load domain; isolated for scaling and data tuning.

## presence-service
Responsibilities:
- online/offline/away state derivation
- heartbeat and room typing command handling
- room presence snapshots
- redis ephemeral state and TTL-driven cleanup

Key files:
- `presence-service/controller/PresenceEdgeCommandController.java`
- `presence-service/service/PresenceService.java`
- `presence-service/state/redis/RedisPresenceEphemeralStateStore.java`
- `presence-service/infrastructure/redis/PresenceRedisPublisher.java`

Boundary rationale:
- high-churn ephemeral domain separated from durable domain services.

## friendship-service
Responsibilities:
- friend request lifecycle
- block/unblock/unfriend
- internal block-status query API for chat
- emits friendship events

Key files:
- `friendship-service/controller/FriendController.java`
- `friendship-service/controller/InternalFriendController.java`
- command/query service layer and kafka producers

Boundary rationale:
- social graph and moderation rules isolated from chat messaging write path.

## notification-service
Responsibilities:
- notification persistence/query/read state
- room mute settings
- consume chat/friend/account kafka events
- emit realtime notification updates

Key files:
- `notification-service/controller/NotificationController.java`
- `notification-service/infrastructure/kafka/MessageCreatedEventConsumer.java`
- application services under `notification-service/application/*`

Boundary rationale:
- async fanout and read-state semantics independent of chat core writes.

## upload-service
Responsibilities:
- two-phase upload flow (`prepare`, `confirm`)
- signed direct-upload integration with Cloudinary

Key files:
- `upload-service/controller/UploadController.java`
- `upload-service/service/UploadSigningService.java`

Boundary rationale:
- media integration and key handling isolated from chat service.

## gateway-service
Responsibilities:
- route matching and rewrite
- JWT resource server + route guard filter
- rate limiting, retry, circuit breaker
- readiness probing of downstream services

Key files:
- `gateway-service/resources/application.yaml`
- `gateway-service/filter/JwtAuthFilterGatewayFilterFactory.java`
- `gateway-service/config/SecurityConfig.java`

Boundary rationale:
- central policy enforcement and edge resilience.

## realtime-edge-service
Responsibilities:
- issue websocket ticket
- validate handshake ticket
- maintain subscriptions and session registry
- route command frames to services
- consume events and deliver to websocket clients

Key files:
- `realtime-edge-service/adapter/in/http/RealtimeTicketController.java`
- `realtime-edge-service/adapter/in/websocket/RealtimeWebSocketHandler.java`
- `realtime-edge-service/subscription/ChannelSubscriptionManager.java`
- `realtime-edge-service/connection/RedisRealtimeSessionRegistry.java`

Boundary rationale:
- decouples websocket lifecycle from domain service internals.
