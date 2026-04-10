## 1. Gateway REST Route Contract Alignment

- [x] 1.1 Update gateway REST route predicates in `chatappBE/gateway-service/src/main/resources/application.yaml` to accept canonical `/api/v1/*` paths for auth, users, chat, presence, friendship, notifications, and uploads
- [x] 1.2 Add scoped rewrite filters for service-labeled routes so `chat` and `friendship` gateway paths forward to downstream controller paths without duplicated prefixes (e.g. `/api/v1/chat/rooms/my` -> `/api/v1/rooms/my`)
- [x] 1.3 Retain backward-compatible route behavior for legacy `/api/*` clients where feasible without breaking canonical `/api/v1/*`

## 2. Frontend REST Path Normalization

- [x] 2.1 Normalize API base composition in `chatappFE/src/config/api.config.ts` (and related client modules) so each service endpoint composes to one canonical path
- [x] 2.2 Update `chatappFE/src/api/user.service.ts` endpoints to avoid duplicated `/users` segments (e.g. use `/me`, `/${id}`, `/bulk`, `/search`, `/me/avatar` against `API_URL.USER`)
- [x] 2.3 Update `chatappFE/src/api/presence.service.ts` and `chatappFE/src/api/notification.service.ts` to remove duplicated service segments in request suffixes
- [x] 2.4 Review/update friendship and chat REST service suffixes to ensure no duplicated segments and consistency with gateway rewrite contract

## 3. Auth Bootstrap Resilience

- [x] 3.1 Add session-aware guards for room/friendship/notification bootstrap effects so protected fetches do not execute in unauthenticated state
- [x] 3.2 Handle 401 + missing refresh token as controlled auth/session transition in bootstrap flows, preventing uncaught promise noise in providers/stores
- [x] 3.3 Clarify friendship websocket lifecycle diagnostics so manual cleanup logs are distinguished from auth-failure disconnects

## 4. Verification

- [x] 4.1 Verify login happy-path loads profile, rooms, friendship unread count, and notifications without 404 on canonical gateway endpoints
- [x] 4.2 Verify browser network paths contain no duplicated segments (`/users/users`, `/presence/presence`, `/notifications/notifications`)
- [x] 4.3 Verify websocket connect/disconnect behavior remains stable across login/logout and no misleading bootstrap `No refresh token` runtime errors are thrown
- [x] 4.4 Run frontend type-check/tests and gateway compile checks to confirm no regressions