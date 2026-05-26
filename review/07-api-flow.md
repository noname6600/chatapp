# 07. API Flow

## Edge Routing
Gateway routes in `gateway-service/resources/application.yaml` map public paths to domain services.

Highlights:
- Auth: `/api/v1/auth/**`
- Users: `/api/v1/users/**`
- Chat/Rooms/Messages: `/api/v1/chat/**`, `/api/v1/rooms/**`, `/api/v1/messages/**`
- Presence: `/api/v1/presence/**`
- Friendship: `/api/v1/friends/**`
- Notifications: `/api/v1/notifications/**`
- Upload: `/api/v1/uploads/**`
- Realtime ticket: `/api/v1/realtime/**`

Gateway applies:
- JwtAuthFilter (adds `X-User-Id` from validated JWT)
- retries on gateway/service unavailable
- circuit breaker per route
- rate limiting via Redis key resolver by IP

## Request Lifecycle (Generic)
1. FE builds request via axios base client (`base.api.ts`).
2. Access token attached in request interceptor.
3. Gateway authenticates JWT and route-matches.
4. Optional filter augmentation (`X-User-Id`).
5. Target service controller validates payload.
6. Application service executes business logic.
7. DB transaction commits.
8. After-commit hooks may publish Kafka/Redis events.
9. API response wrapped in `ApiResponse` format.
10. FE updates state and optionally websocket reconciliation catches async updates.

## Error Propagation
- Service-level business errors map via `common-web/GlobalExceptionHandler`.
- Gateway fallback path returns 503 payload when circuit breaker opens.
- FE refresh interceptor avoids retries for login/register/refresh endpoints.

## Internal APIs
Some internal endpoints exist for cross-service checks, for example:
- friendship block check for chat path
- user bulk lookup for chat/friendship UI enrichment

These endpoints require strong network and service-level auth guarantees.

## Why This API Design Exists
- Single ingress endpoint simplifies FE configuration and CORS.
- Path-based routing keeps service URLs hidden from clients.
- Gateway resilience controls avoid exposing raw downstream outages directly.

## Tradeoffs
- More policy logic in gateway means greater blast radius if misconfigured.
- Duplicated auth validation (gateway + service) increases CPU overhead but improves defense-in-depth.
