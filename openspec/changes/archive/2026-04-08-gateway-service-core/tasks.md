## 1. Module Setup

- [x] 1.1 Create `gateway-service/build.gradle` with Spring Cloud Gateway, Resilience4j, Redis reactive, Security, and JJWT dependencies
- [x] 1.2 Add `gateway-service` to root `settings.gradle`
- [x] 1.3 Create `GatewayApplication.java` main class under `com.example.gateway`
- [x] 1.4 Create `gateway-service/src/main/resources/application.yaml` with server port `8080` and Spring application name

## 2. Routing Configuration

- [x] 2.1 Define routes for all HTTP services in `application.yaml`: auth (`/api/auth/**`), user (`/api/users/**`), chat (`/api/chat/**`), presence (`/api/presence/**`), friendship (`/api/friendship/**`), notification (`/api/notifications/**`), upload (`/api/upload/**`)
- [x] 2.2 Define WebSocket routes for chat (`/ws/chat/**`), presence (`/ws/presence/**`), and friendship (`/ws/friendship/**`) using `ws://` URI scheme
- [x] 2.3 Inject service hostnames and ports from environment variables (e.g. `${AUTH_SERVICE_HOST:auth-service}`) so routes work in both Docker and local development
- [x] 2.4 Attach named `CircuitBreaker` filter to each HTTP route with `fallbackUri: forward:/fallback/service-unavailable`

## 3. JWT Auth Filter

- [x] 3.1 Create `JwtAuthGatewayFilterFactory` implementing `AbstractGatewayFilterFactory` under `com.example.gateway.filter`
- [x] 3.2 JWT validation delegated to Spring Security OAuth2 Resource Server (JWKS from auth-service) — rejects invalid/expired tokens with 401 before filter runs
- [x] 3.3 Extract user ID claim from valid JWT and set `X-User-Id` header on the forwarded request
- [x] 3.4 Register `JwtAuthFilter` bean name so it can be referenced by name in `application.yaml` route filters

## 4. Security Configuration

- [x] 4.1 Create `SecurityConfig.java` with a reactive `SecurityWebFilterChain` bean
- [x] 4.2 Permit all requests to `/api/auth/**` and `/actuator/**` paths; require authentication for all other paths
- [x] 4.3 Disable CSRF (stateless API gateway)
- [x] 4.4 Configure CORS policy in `SecurityConfig`: allowed origins from `${CORS_ALLOWED_ORIGINS}`, methods GET/POST/PUT/PATCH/DELETE/OPTIONS, allow credentials, expose Authorization header

## 5. Rate Limiting

- [x] 5.1 Configure Redis connection in `application.yaml` using `${REDIS_HOST}` and `${REDIS_PORT}` environment variables
- [x] 5.2 Create `IpKeyResolver` bean implementing `KeyResolver` that extracts client IP from the request
- [x] 5.3 Add `RequestRateLimiter` to the `default-filters` section in `application.yaml` with replenish rate 20, burst capacity 40
- [x] 5.4 Set `deny-empty-key: false` on rate limiter to allow traffic when Redis is unavailable

## 6. Circuit Breaker Configuration

- [x] 6.1 Add `resilience4j.circuitbreaker` configuration to `application.yaml` with a default config: sliding window 10, failure rate 50%, open wait 10s, half-open permitted 3
- [x] 6.2 Define named instances for each route circuit breaker: auth-cb, user-cb, chat-cb, presence-cb, friendship-cb, notification-cb, upload-cb — all referencing the default config
- [x] 6.3 Create `FallbackController.java` with a `GET /fallback/service-unavailable` endpoint that returns HTTP 503 with a JSON error body

## 7. Observability

- [x] 7.1 Add Actuator dependency and expose `health`, `info`, `metrics`, `prometheus` endpoints in `application.yaml`
- [x] 7.2 Set `management.endpoint.health.show-details: always` to expose component details
- [x] 7.3 Verify `/actuator/health/liveness` and `/actuator/health/readiness` are accessible (Spring Boot default probe paths)

## 8. JWT Secret Configuration

- [x] 8.1 JWT validation uses JWKS URI (Spring Security OAuth2 Resource Server) — no shared secret needed; `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` configured instead
- [x] 8.2 JwtProperties not needed — JWKS URI config handled by Spring Security auto-configuration
- [x] 8.3 JwtAuthFilterGatewayFilterFactory reads Jwt principal from Spring Security context (no manual injection needed)

## 9. Local Development Support

- [x] 9.1 Create `application-local.yaml` with hardcoded service addresses (`localhost:8081` etc.) and a test JWT secret for local runs without Docker
- [ ] 9.2 Verify the gateway starts with `./gradlew :gateway-service:bootRun` against locally running services

## 10. Docker Compose Integration

- [x] 10.1 Add `gateway-service` to the project `docker-compose.yml` with `PORT=8080`, all service host/port env vars, `REDIS_HOST`, `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS`
- [x] 10.2 Connect gateway to the same Docker Compose internal network as all microservices
- [x] 10.3 Set `restart: unless-stopped` and a health check using `curl -f http://localhost:8080/actuator/health`
- [x] 10.4 Expose only port 8080 on the gateway; remove any previously exposed ports on individual microservices
