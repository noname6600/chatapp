# 12. Production Behavior

## 1) Deployment Shape
docker-compose.local.yml shows core runtime planes:
- data plane: PostgreSQL
- cache/coordination plane: Redis
- event plane: Kafka + Zookeeper
- ingress plane: gateway-service
- realtime plane: realtime-edge-service
- domain services: auth/user/chat/presence/friendship/notification/upload

## 2) Runtime Characteristics By Plane

### Gateway plane
- central auth validation, route rewrite, retry, circuit breaker, rate limiting
- failure in gateway policy impacts all API traffic

### Event plane (Kafka)
- durable async propagation
- consumer recovery behavior depends on service-specific error handlers

### Realtime plane (Redis + WebSocket)
- low latency push
- non-durable fanout path
- edge session ownership and lease model

### Domain planes
- each service has own DB/schema ownership but shared event contracts

## 3) Security Behavior In Runtime
- gateway validates JWT issuer and timestamps
- services also validate JWT as resource servers
- internal service credential headers protect selected internal paths
- websocket handshake uses one-time ticket + Redis token reference model

Production implication: layered auth defense exists, but token/header configuration drift can create gaps.

## 4) Scheduler Runtime Behavior
Observed scheduled tasks:
- auth refresh token cleanup every 30 minutes
- auth JWT key cleanup hourly
- key manager cleanup fixedDelay 10 minutes
- realtime edge stale session cleanup every 30s default

These jobs are local-per-instance unless externally coordinated.

## 5) Stateful Runtime Behavior
Stateful components:
- Redis session leases and subscription indexes
- Redis token references for websocket sessions
- presence ephemeral user/room state
- dedupe keys for event processing windows

Stateless components:
- gateway request pathing logic
- most command handlers (excluding DB writes)

## 6) Throughput/Latency Shaping Controls
- gateway request rate limiter via Redis
- websocket per-session outbound queue capacity
- service HTTP timeout settings for edge->presence/friendship/chat/notification calls
- kafka retry backoff policies in selected services

## 7) Startup and Readiness Behavior
- gateway readiness includes downstream health checks list
- compose uses health checks for postgres/redis/gateway
- presence startup checks Redis keyspace notifications when enabled

## 8) Runtime Tradeoff: Dual Event Planes
Message flow is intentionally dual-plane:
- Kafka for durability and backend side effects
- Redis for realtime user experience

Consequence:
- users can see event in UI before all durable side effects complete
- or durable side effect can complete without realtime push if Redis path fails

## 9) Configuration-Driven Behavior Toggling
Critical toggles:
- REALTIME_SESSION_REGISTRY_MODE redis vs in-memory
- realtime.redis.listener.enabled
- realtime.dispatch.handoff.enabled
- presence.redis.keyspace-notification.check.fail-on-missing
- internal auth tokens per service

These flags significantly alter runtime semantics.

## 10) Production Observability Surface
- TraceIdFilter + X-Trace-Id propagation in HTTP
- event metadata correlation IDs in async paths
- Micrometer counters in delivery/handoff/session cleanup paths
- actuator health/info/metrics endpoints exposed in several services

## 11) Notable Production Risks
- Without durable outbox, DB commit and event publish atomicity is best-effort.
- Redis pub/sub loss is acceptable only if clients have robust reconciliation strategy.
- Internal auth token defaults in config may be unsafe if not overridden in production.
- Per-service inconsistency in Kafka recovery settings increases operational complexity.
