## Context

The frontend (`chatappFE`) communicates with backend microservices across multiple ports (8081-8088). The backend has deployed an API gateway (`gateway-service`) on port 8080 that handles routing, authentication, rate limiting, and circuit breaking. The frontend must now be updated to route all requests through this single gateway entry point.

**Current State:**
- REST API clients hardcoded to individual service URLs (e.g., `http://localhost:8081/api/v1` for auth-service)
- WebSocket connections likely also point to individual services
- No environment-based configuration for backend URLs
- API configuration in `src/config/api.config.ts` and `src/api/clients.ts`

**Backend Gateway:**
- Single entry point: `http://localhost:8080` (local dev) or `https://api.example.com` (production)
- Path-based routing: `/api/v1/auth/*` → auth-service, `/api/v1/chat/*` → chat-service, etc.
- All routes require JWT authentication (except `/api/auth/login`, `/api/auth/refresh`)
- Rate limiting and circuit breakers configured per route
- CORS configured for `http://localhost:5173` (local dev)

## Goals / Non-Goals

**Goals:**
- Centralize all backend communication through a single gateway URL
- Support both development (localhost:8080) and production gateway URLs via environment configuration
- Update all REST API clients to use the gateway while preserving service-specific routing paths
- Update all WebSocket endpoints to use the gateway
- No breaking changes to frontend API surface (internal URLs change, not public interfaces)

**Non-Goals:**
- Modifying frontend authentication logic (JWT handling remains the same)
- Changing API request/response contracts
- Adding new features or functionality to the frontend
- Performance optimization beyond the gateway itself

## Decisions

**Decision 1: Centralized Gateway URL Configuration**
- **Choice**: Store gateway base URL in environment configuration, not hardcoded in code
- **Rationale**: Enables same build artifacts for dev/staging/production (12-factor app principle)
- **Implementation**: Update `src/config/api.config.ts` to read from environment variables or have a single configurable base URL
- **Alternatives Considered**:
  - Keep individual service URLs hardcoded: Requires code changes for each environment (rejected)
  - Service discovery (e.g., fetch service URLs from backend): Over-engineered for this use case (rejected)

**Decision 2: Single Gateway Base URL for All Services**
- **Choice**: Use `http://localhost:8080/api/v1` as base, remove individual service URLs
- **Rationale**: Gateway handles path-based routing (e.g., `/auth/*` → auth-service, `/chat/*` → chat-service)
- **Implementation**: Replace all individual URLs with gateway URL in `api.config.ts`
- **Alternatives Considered**:
  - Maintain per-service URLs and add gateway fallback: Adds complexity, defeats purpose (rejected)
  - Separate REST and WebSocket base URLs: WebSocket also routes through gateway, can share base (rejected)

**Decision 3: API Configuration Structure**
- **Choice**: Keep `src/config/api.config.ts` as single source of truth, but simplify to `API_BASE_URL` + path structure
- **Rationale**: Easier to maintain, clearer that all services use same gateway
- **Implementation**: 
  ```typescript
  const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';
  export const API_ENDPOINTS = {
    auth: `${API_BASE_URL}/auth`,
    user: `${API_BASE_URL}/user`,
    chat: `${API_BASE_URL}/chat`,
    // ... etc
  };
  ```
- **Alternatives Considered**:
  - Keep URL_BY_SERVICE structure: Requires updating every service when gateway becomes available (rejected)

**Decision 4: WebSocket Gateway Integration**
- **Choice**: Update WebSocket connections to use gateway protocol and host
- **Rationale**: WebSocket routes also defined in gateway configuration; centralized routing
- **Implementation**: Search for WebSocket URL configuration (likely in `src/websocket/` or individual service files), update to use gateway host
- **Alternatives Considered**:
  - Keep WebSocket direct to services: Bypasses gateway security controls (rate limiting, circuit breakers) (rejected)
  - Use HTTP long-polling instead: Defeats real-time capabilities (rejected)

## Risks / Trade-offs

**Risk: Gateway becomes single point of failure**
→ Mitigation: Backend team has implemented circuit breakers and health checks; gateway auto-restarts if unhealthy. Not in scope for FE.

**Risk: Environment variable not set, uses localhost:8080 in production**
→ Mitigation: Use explicit environment file (`.env.production`) and document setup clearly. CI/CD must inject correct URL at build time.

**Risk: Network latency through gateway vs. direct service calls**
→ Mitigation: Negligible for local dev. Gateway optimized for throughput (Spring Cloud Gateway is reactive/non-blocking). Not using request aggregation, so no additional latency.

**Risk: WebSocket path differs from REST API path**
→ Mitigation: Verify exact WebSocket endpoint structure with backend gateway config before implementation. Paths should follow same `/api/v1/{service}/*` pattern.

## Migration Plan

1. **Phase 1: Update REST API Configuration**
   - Update `src/config/api.config.ts` to use single gateway URL
   - Update `src/api/clients.ts` to reference new configuration
   - Test: All REST API calls route through gateway (8080)

2. **Phase 2: Update WebSocket Endpoints**
   - Locate WebSocket connection code (search for `ws://localhost:8084`, etc.)
   - Update to use gateway WebSocket routes
   - Test: Real-time chat, presence, friendship notifications work through gateway

3. **Phase 3: Environment Configuration**
   - Create/update `.env.local` and `.env.production` with correct API URLs
   - Document environment setup in README
   - Test: Dev and production builds use correct gateway URLs

4. **Phase 4: Validation**
   - Local dev: Run with docker-compose.local.yml, all endpoints via localhost:8080
   - No breaking changes to app functionality
   - Rollback: Revert `api.config.ts` changes to restore individual service URLs

## Open Questions

1. Exact WebSocket endpoint structure in gateway? (e.g., `ws://localhost:8080/api/v1/chat/ws` or other pattern?)
2. Are WebSocket URLs defined in a central configuration file or scattered across components?
3. Should we support fallback to individual services for testing purposes, or fully commit to gateway?
4. Are there any service-specific URL patterns (query params, custom headers) that might be affected by gateway routing?
