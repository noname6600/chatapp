## Context

The chatapp backend is composed of multiple Spring Boot microservices (auth, user, chat, presence, friendship, notification, upload) each running on distinct internal ports. Currently there is no unified entry point — clients must know individual service addresses. Cross-cutting concerns like JWT validation, CORS, and rate limiting are either duplicated or absent. The system needs a production-grade API gateway before Docker Compose deployment.

## Goals / Non-Goals

**Goals:**
- Single ingress point (`gateway-service` on port 8080) for all HTTP and WebSocket traffic
- Centralized JWT validation; downstream services trust forwarded user identity
- CORS enforced once at gateway level
- Per-IP Redis-backed rate limiting on all routes
- Per-route Resilience4j circuit breakers with fallback responses
- Native WebSocket proxying for chat, presence, and friendship real-time channels
- Prometheus metrics and Actuator health endpoints
- Static Docker Compose DNS routing (no service registry) — scalable to `lb://` later

**Non-Goals:**
- Service discovery or dynamic registration (Eureka, Consul)
- Multi-instance load balancing in this iteration (architecture ready, not wired)
- OAuth2 login flows at gateway (handled by auth-service directly)
- API versioning strategy
- Nginx configuration (handled separately at infrastructure layer)

## Decisions

### 1. Spring Cloud Gateway (WebFlux) over alternatives
Spring Cloud Gateway is reactive (Netty, WebFlux), has native WebSocket proxy support, integrates with Resilience4j and Redis rate limiting out of the box, and is in the same Spring ecosystem as all downstream services. Traefik was considered but lacks native JWT filter logic without plugins; Kong adds Postgres dependency and ops overhead.

### 2. Static `http://` URIs over service registry
Docker Compose assigns predictable DNS names per service. No Eureka/Consul process to operate, no client-side registration logic. When scaling to multiple instances, routes switch from `http://service-name:port` to `lb://service-name` with Spring Cloud LoadBalancer — a one-line YAML change per route plus `spring-cloud-starter-loadbalancer` dependency.

### 3. JWT validation as a custom `GatewayFilter` bean
A `JwtAuthFilter` (implements `GatewayFilterFactory`) reads the `Authorization: Bearer <token>` header, validates signature and expiration using JJWT, and sets `X-User-Id` and `X-User-Email` headers on the forwarded request. Public paths (auth endpoints) are excluded via Spring Security permit rules. Downstream services read identity from headers without re-validating the token.

### 4. CORS at gateway only
All downstream services currently define their own CORS configuration. CORS will be defined once in the gateway's `SecurityWebFilterChain` and removed from individual services over time. This prevents duplicate allow-origin policies from diverging.

### 5. Rate limiting via Redis `RequestRateLimiter`
Gateway's built-in `RequestRateLimiter` GatewayFilter uses a token-bucket algorithm backed by Redis. Key resolver defaults to remote IP (`IpKeyResolver` bean). Replenish rate: 20 req/s, burst: 40. Redis is already an infrastructure dependency (presence-service, chat-service) so no new infrastructure is added.

### 6. Resilience4j circuit breaker per route
Each route gets its own named circuit breaker. Sliding window of 10 calls, 50% failure threshold, 10s open wait, forward to `/fallback/service-unavailable` on open state. This isolates failures — a failing upload-service will not affect chat-service traffic.

### 7. WebSocket routes using `ws://` scheme
Spring Cloud Gateway transparently upgrades HTTP connections to WebSocket when the route URI uses the `ws://` scheme. No extra config needed. WebSocket routes do not apply the JWT filter (token is passed as a query param or subprotocol at connect time, validated by the downstream service).

## Risks / Trade-offs

- **Single point of failure** → Gateway itself becomes critical path. Mitigate: Docker health checks + restart policy (`restart: unless-stopped`), Nginx upstream failover when multiple gateway instances are added later.
- **JWT secret shared with gateway** → Gateway needs `JWT_SECRET` env var. Mitigate: inject via Docker secret or env file not committed to source control.
- **Redis unavailability breaks rate limiting** → If Redis goes down, `RequestRateLimiter` will deny requests by default. Mitigate: set `deny-empty-key: false` in rate limiter config so traffic passes through if Redis is unreachable.
- **WebSocket auth bypass** → WebSocket routes skip JWT filter at gateway. Mitigate: downstream WebSocket handlers validate token on handshake independently.
- **CORS centralization migration** → Individual services still have CORS config during transition. Mitigate: track removal in tasks list, test after each service migration.

## Migration Plan

1. Deploy `gateway-service` as a new Docker Compose service alongside existing services.
2. Update Nginx upstream to point to `gateway:8080` instead of individual services.
3. Verify all HTTP and WebSocket routes work end-to-end through gateway.
4. Remove CORS configuration from individual services progressively.
5. Future: change `http://` to `lb://` per service when scaling instances with `--scale`.

## Open Questions

- Should WebSocket routes also validate JWT at gateway level (via query param resolver)? Currently delegated to downstream.
- Should `X-User-Id` header injection happen at gateway or remain downstream responsibility?
- Tracing: add OpenTelemetry agent as JVM arg at gateway for distributed trace propagation?
