# 09. Cross-Service Communication

## 1) Communication Modalities
The system uses four communication modes:
1. synchronous HTTP via gateway
2. internal HTTP between services (RestClient/Feign)
3. asynchronous Kafka events
4. asynchronous Redis pub/sub for realtime fanout

## 2) Gateway-Mediated External API Calls
Gateway routes are in gateway-service application.yaml.

Key behaviors:
- path rewrite rules normalize v1 and legacy paths
- JwtAuthFilter adds X-User-Id from validated JWT subject
- Retry filter on transient statuses
- CircuitBreaker fallback to /fallback/service-unavailable
- Redis-backed rate limiting via RequestRateLimiter

Websocket ingress route:
- /ws/** rewritten to /realtime and forwarded to realtime-edge ws URI.

## 3) Internal HTTP Calls

### Realtime edge -> presence-service
PresenceDomainClient uses RestClient with:
- connect/read timeouts
- optional internal auth header injection
- bearer relay for user context

Operations called:
- connect/disconnect/heartbeat
- joinRoom/leaveRoom
- typing/stopTyping
- globalSnapshot/roomSnapshot

### Chat/friendship -> user-service
Feign clients used for profile lookups and username search.

common-feign interceptors:
- FeignTraceConfig adds X-Trace-Id
- FeignJwtConfig relays bearer token from security context

## 4) Internal Auth Filters
Service-specific internal endpoint protection:
- friendship InternalServiceAuthFilter protects /api/v1/internal/**
- presence InternalServiceIngressAuthFilter protects /api/v1/presence/ws/**

Both rely on header token equality checks and fail-fast in production when token missing.

## 5) Async Kafka Communication
Used for durable inter-service domain events.

Examples:
- auth -> user/notification via account.created
- friendship -> notification/chat/realtime-edge via friendship topics
- chat -> notification via chat.message.sent and related topics

## 6) Async Redis Communication
Used for realtime low-latency event fanout.

Examples:
- chat -> realtime-edge via realtime.chat.room.{roomId}
- presence -> realtime-edge via realtime.presence.*
- notification -> realtime-edge via realtime.notification.user.{userId}

## 7) End-to-End Command Split
Typical user command split:
- command accepted synchronously via API/websocket route
- durable side effects via DB + Kafka
- immediate UI synchronization via Redis+WebSocket

## 8) Correlation And Observability Propagation
- TraceIdFilter sets/propagates X-Trace-Id in HTTP paths
- Feign propagates trace IDs downstream
- Event metadata carries correlationId and eventId

This enables correlation across sync and async boundaries.

## 9) Backpressure And Load Considerations
- gateway rate limiter guards ingress burst
- websocket outbound queue bounds per-session buffering
- Kafka decouples producers and consumers under load

Potential bottlenecks:
- Redis as central pub/sub and session registry backend
- gateway as single ingress policy choke point

## 10) Consistency Across Communication Modes
Potential mismatch windows:
- REST response succeeds but Kafka publish fails after commit
- Redis realtime event delivered before consumer-updated read models converge
- websocket client misses events during disconnect and needs API resync

System relies on eventual convergence rather than strict immediate global consistency.

## 11) Communication Contract Governance
Contracts are centralized in common-events payloads and event enums.

Operationally this means:
- adding/changing event payloads requires coordinated rollout discipline
- consumers should tolerate unknown/missing fields for compatibility
- schema tests become critical at scale
