## Why

The application currently has no unified entry point — clients connect directly to individual microservices, making it impossible to enforce auth, CORS, rate limiting, and observability consistently. A production-ready API gateway is needed now to consolidate cross-cutting concerns before deploying to Docker Compose at scale.

## What Changes

- Introduce a new `gateway-service` Spring Boot module as the sole public entry point
- All HTTP and WebSocket traffic from clients is routed through the gateway to downstream services
- JWT validation moves to the gateway; individual services can trust forwarded identity headers
- CORS policy is defined once at the gateway and removed from individual services
- Per-user/IP rate limiting enforced at gateway via Redis-backed `RequestRateLimiter`
- Resilience4j circuit breakers protect each upstream route from cascading failures
- WebSocket routes proxied natively for chat, presence, and friendship real-time channels
- Actuator health/metrics endpoints exposed for Docker health checks and Prometheus scraping
- Routes use static Docker Compose service DNS (`http://auth-service:8081`) — no service registry
- Architecture is designed to switch to `lb://` load-balanced URIs when scaling to multiple instances

## Capabilities

### New Capabilities
- `gateway-routing`: Central HTTP and WebSocket routing to all microservices via static Docker DNS URIs
- `gateway-jwt-auth`: Reactive JWT validation filter at gateway level; public routes bypass auth
- `gateway-rate-limiting`: Per-IP Redis-backed rate limiting using Spring Cloud Gateway's built-in filter
- `gateway-circuit-breaker`: Per-route Resilience4j circuit breakers with fallback responses
- `gateway-cors`: Single CORS policy enforced at gateway for all downstream services
- `gateway-observability`: Actuator health endpoints and Micrometer/Prometheus metrics

### Modified Capabilities
<!-- No existing spec-level requirements are changing. Cross-cutting concerns (CORS, auth filters) previously scattered across services will move to gateway, but individual service behavior specs remain unchanged. -->

## Impact

- New Gradle submodule: `gateway-service` added to `settings.gradle`
- New dependencies: `spring-cloud-starter-gateway`, `spring-cloud-starter-circuitbreaker-reactor-resilience4j`, `spring-boot-starter-data-redis-reactive`, `spring-boot-starter-security`, JJWT
- All client-facing ports consolidated behind gateway port `8080`; individual service ports become internal-only
- Docker Compose network: gateway joins the same bridge network as all services; Nginx proxies to gateway only
- Redis dependency added to gateway (already used by presence and chat services)
- Future scaling path: change `http://` URIs to `lb://` and add `spring-cloud-starter-loadbalancer` when multiple instances are needed
