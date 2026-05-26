# 01. Architecture

## Structural Decomposition
Backend modules are declared in `chatappBE/settings.gradle`.

Services:
- auth-service
- user-service
- chat-service
- presence-service
- notification-service
- friendship-service
- upload-service
- gateway-service
- realtime-edge-service

Shared modules:
- common-core
- common-web
- common-security
- common-feign
- common-events
- common-kafka
- common-redis
- common-redis-cache

## Dependency Direction
Expected dependency direction is:
- feature services -> common modules
- gateway/realtime-edge -> common modules + feature service APIs
- common modules should not depend on business services

Observed direction mostly follows this, but some common modules are broad and infrastructure-heavy, increasing coupling pressure.

## Layering Pattern Inside Services
Typical stack:
- controller/inbound adapter
- application service / pipeline
- domain entities + repositories
- outbound adapters (Kafka/Redis/Feign)

Examples:
- `chat-service/modules/message/controller/MessageCommandController.java`
- `chat-service/modules/message/application/pipeline/send/PublishMessageEventStep.java`
- `chat-service/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`

## Sync vs Async Boundaries
Sync:
- gateway -> service REST routing
- realtime-edge -> domain command endpoints (`presence/ws`, friendship/chat command routers)
- chat -> user/friendship via Feign

Async:
- domain events via Kafka topics in `common-kafka/topic/KafkaTopics.java`
- pub/sub fanout via Redis channels in `common-redis/channel/RedisChannels.java`

## Realtime Architecture
Realtime ingress is centralized in `realtime-edge-service`:
- ticket issuance: `adapter/in/http/RealtimeTicketController.java`
- handshake auth: `adapter/in/websocket/JwtHandshakeInterceptor.java`
- frame handling: `adapter/in/websocket/RealtimeWebSocketHandler.java`
- delivery services: `delivery/*RealtimeDeliveryService.java`
- session registry: in-memory or Redis mode (`realtime.session-registry.mode`)

This enables horizontal websocket scaling while delegating domain writes to domain services.

## Resilience Mechanics
At gateway:
- retries on BAD_GATEWAY/SERVICE_UNAVAILABLE
- circuit breakers per domain route
- Redis rate limiter
- downstream readiness indicator

At messaging layer:
- dedupe guards in some consumers (for example friendship in realtime-edge)
- after-commit publish in send-message pipeline to reduce transactional mismatch

## Failure Behavior (High-Level)
- Gateway fallback emits 503 payload when circuits open.
- Redis pub/sub failures generally degrade realtime fanout but not necessarily durable command path.
- Kafka consumer failures are service-specific; there is no globally enforced DLQ strategy in all consumers.

## Runtime Topology Implication
Two important implications:
- User-perceived consistency is eventually consistent across services, but realtime paths can look strongly consistent when Redis/Kafka are healthy.
- During infra degradation, REST writes may succeed while websocket updates lag or drop.

## Architecture Decision Tradeoffs
Why this approach is common:
- Split durable propagation (Kafka) from low-latency fanout (Redis).
- Keep domain writes in owning service.
- Keep websocket orchestration in dedicated edge service.

Tradeoffs:
- More moving parts and more partial-failure modes.
- Complex observability and correlation requirements.
- Harder onboarding unless event contracts are rigorously documented.
