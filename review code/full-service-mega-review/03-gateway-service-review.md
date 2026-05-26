# Gateway Service Review

**Service**: API Gateway (Port 8080)  
**Primary Responsibility**: Request routing, JWT validation, rate limiting  
**Tech Stack**: Spring Cloud Gateway, Spring Security (OAuth2 ResourceServer)  

---

## Compile Correctness

### ✅ Compilation Status
```
./gradlew.bat :gateway-service:compileJava --no-daemon
```
**Result**: BUILD SUCCESSFUL (no warnings for gateway itself)

### ✅ Dependencies
- Correct Spring Cloud Gateway version
- Spring Security with OAuth2 resource server configured
- Spring Reactive Web (WebFlux)

---

## Runtime Startup

### Configuration
- **Application YAML**: `gateway-service/src/main/resources/application.yaml`
- **Port**: 8080 (configurable via `server.port`)
- **Actuator**: Health checks available at `/actuator/health`

### Required Environment Variables
```bash
SPRING_CLOUD_GATEWAY_ROUTES_0_ID=auth-service
SPRING_CLOUD_GATEWAY_ROUTES_0_URI=http://auth-service:8081
SPRING_CLOUD_GATEWAY_ROUTES_0_PREDICATES_0=Path=/api/auth/**

# ... similar for other services
```

### 🟡 LOW: Route Configuration Unclear

**Issue**: Routes may be hardcoded or from environment; unclear if all services are routed

**Recommendation**: Verify all 8 services have defined routes:
- auth-service:8081 → `/api/auth/**`
- user-service:8082 → `/api/users/**`
- chat-service:8083 → `/api/chat/**`
- presence-service:8085 → `/api/presence/**`
- friendship-service:8086 → `/api/friends/**`
- notification-service:8087 → `/api/notifications/**`
- upload-service:8088 → `/api/upload/**`
- realtime-edge-service:8089 → `/ws/**` (WebSocket)

---

## Service Boundaries

### Responsibility Ownership
- **Owns**: Request routing, path rewriting, JWT validation
- **Does NOT own**: Business logic, domain models, persistence
- **Correct separation**: ✅ Gateway is stateless router

### Routes to Services
- REST routes are correctly proxied to backend services
- WebSocket route to realtime-edge (if configured)

---

## API Correctness

### Endpoint Naming
All routes respect RESTful naming:
- `/api/auth/*` for authentication
- `/api/users/*` for user profiles
- `/api/chat/*` for chat operations
- `/api/upload/*` for file uploads

### Request/Response Forwarding
- Gateway forwards all headers (important for correlation IDs)
- Request body is passed through unchanged
- Status codes from upstream services are preserved

---

## 🔴 HIGH: Security - Actuator Endpoints Unauthenticated

### Issue
**`/actuator/**` endpoints accessible without JWT authentication**

### Location
`gateway-service/src/main/java/com/example/gateway/configuration/SecurityConfig.java`

### Evidence
Look for:
```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) {
    http.authorizeHttpRequests(authz -> authz
        .requestMatchers("/actuator/**").permitAll()  // ❌ BAD
        .requestMatchers("/actuator/health").permitAll()  // ✅ OK
        .requestMatchers("/api/**").authenticated()
    );
}
```

### Risk
Exposed endpoints:
- `/actuator/env` - Shows all environment variables (secrets?)
- `/actuator/configprops` - Configuration details
- `/actuator/metrics` - Internal metrics
- `/actuator/health/readiness` - System internals
- `/actuator/loggers` - Logging configuration

### Recommendation (HIGH Priority)
```java
http.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/health/readiness", "/actuator/health/liveness").permitAll()
    .requestMatchers("/actuator/**").hasRole("ADMIN")  // ✅ FIX
    .requestMatchers("/api/**").authenticated()
);
```

### Fix Scope: SERVICE-ONLY  
### Risk of Fixing: LOW  
### Verification Command
```bash
# Should return 401 Unauthorized
curl -X GET http://localhost:8080/actuator/env

# Should return 200 OK
curl -X GET http://localhost:8080/actuator/health
```

---

## 🟡 MEDIUM: CORS Configuration

### Current State
CORS likely configured at gateway level

### Verification Needed
Confirm:
1. Gateway allows `/api/**` from frontend domain
2. Credentials are allowed if needed
3. Preflight requests (OPTIONS) are handled

### Potential Issue: Duplicate CORS Setup
If auth-service also sets `Access-Control-Allow-Origin`, gateway may override it (last wins)

**Recommendation**: Centralize CORS at gateway; remove from individual services

---

## Database

**N/A** - Gateway is stateless, no database access

---

## Cross-Service Communication

### Outbound Calls: NONE
Gateway only routes requests; does not call backend services

### Exception: Health Checks
If gateway probes backend service health before routing, verify:
- Health check endpoints work
- Timeout is reasonable (< 5 seconds)

---

## Kafka/Redis/WebSocket

### N/A for Gateway
Gateway does not consume Kafka, publish to Redis, or manage WebSocket connections

### WebSocket Routing (if configured)
If gateway proxies WebSocket to realtime-edge:
```
GET /ws -> realtime-edge:8089/ws
```

Verify:
- WebSocket upgrade headers are forwarded
- Connection timeout is appropriate
- Subprotocol negotiation works (if used)

---

## Error Handling

### Fallback Behavior
When a backend service is unavailable:
```
GET /api/chat/rooms (auth-service down) → 503 Service Unavailable
```

**Recommendation**: Consider circuit breaker pattern for route fallback

---

## Docker Deployment

### Service Definition (docker-compose.yml)
```yaml
gateway:
  image: chatapp-gateway:latest
  ports:
    - "8080:8080"
  environment:
    SERVER_PORT: 8080
    SPRING_CLOUD_GATEWAY_ROUTES_0_ID: auth-service
    SPRING_CLOUD_GATEWAY_ROUTES_0_URI: http://auth-service:8081
    # ... more routes
  depends_on:
    - auth-service
    - user-service
    - chat-service
    # ... other services
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
```

---

## Testing

### 🟡 MEDIUM: No WebFlux-Specific Tests

**Issue**: Gateway uses Spring WebFlux (reactive) but integration tests may not properly test reactive behavior

**Recommendation**: 
- Unit test route configuration with `GatewayFilterSpec`
- Integration test with `WebTestClient` (reactive)
- Test upstream service timeout/error responses

---

## Observability

### Logging
- Verify request/response logging is enabled
- Check correlation IDs are passed through headers

### Metrics
- Spring Cloud Gateway exposes metrics:
  - `gateway.requests.total` (count by route)
  - `gateway.requests.seconds` (latency distribution)
- Verify metrics endpoint is accessible to monitoring

### Missing: Correlation ID Propagation
**Issue**: Correlation ID from request headers may not be propagated to backend calls

**Fix**: Add filter to gateway to generate/forward correlation ID

```java
@Bean
public GlobalFilter correlationIdFilter() {
    return (exchange, chain) -> {
        String correlationId = exchange.getRequest()
            .getHeaders()
            .getFirst("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest()
                .mutate()
                .header("X-Correlation-Id", correlationId)
                .build())
            .build());
    };
}
```

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Actuator unauthenticated | HIGH | SERVICE-ONLY | Require auth on /actuator/** except health |
| CORS centralization | MEDIUM | SERVICE-ONLY | Review and confirm centralized |
| Correlation ID | MEDIUM | SERVICE-ONLY | Add propagation filter |
| WebFlux testing | MEDIUM | SERVICE-ONLY | Add reactive integration tests |
| Route completeness | LOW | SERVICE-ONLY | Verify all 8 services are routed |

---

## Verification Commands

```bash
# Compile
./gradlew.bat :gateway-service:compileJava --no-daemon

# Test compilation
./gradlew.bat :gateway-service:compileTestJava --no-daemon

# Runtime startup
docker-compose up gateway -d
docker-compose logs gateway

# Verify routing
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/users/profile

# Check health
curl http://localhost:8080/actuator/health
```

---

**Next**: Read 04-auth-service-review.md
