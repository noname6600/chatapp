## Why

After login, many frontend calls fail with `404` and `401`, including `/api/v1/users/users/me`, `/api/v1/chat/rooms/my`, friendship unread count, and notifications. The current gateway route contract and frontend REST path composition are inconsistent (mixed `/api/...` vs `/api/v1/...` and duplicated service segments), causing broad endpoint misses and cascading auth-refresh errors such as `No refresh token`.

## What Changes

- Align gateway REST route predicates with the frontend `VITE_API_URL` contract (`/api/v1/...`) while preserving backward compatibility for current `/api/...` consumers.
- Add gateway path rewrite/strip behavior where service prefixes are gateway-only labels (for example `chat`, `friendship`) and should not be forwarded as duplicated path segments to downstream controllers.
- Normalize frontend REST service modules to avoid duplicated segments (for example base `/users` + request `/users/me`) and match gateway canonical paths.
- Add frontend auth-bootstrap guard behavior so protected bootstrap fetches (rooms, notifications, friendship unread count) fail gracefully: no unhandled promise noise, no misleading `No refresh token` surface in feature stores, and clear session-invalid handling.

## Capabilities

### New Capabilities
- `fe-auth-bootstrap-resilience`: Auth-dependent bootstrap flows (room preload, friendship unread preload, notifications preload) handle missing/expired session consistently and avoid noisy unhandled runtime errors.

### Modified Capabilities
- `gateway-routing`: Route predicates and forwarded request paths must be canonical for `/api/v1/*` traffic and support required rewrite behavior.
- `fe-rest-api-gateway-integration`: Frontend REST API base URLs and endpoint suffixes must compose to valid gateway paths without duplicated service segments.

## Impact

- Gateway routing config: `chatappBE/gateway-service/src/main/resources/application.yaml`
- Frontend API config and service modules: `chatappFE/src/config/api.config.ts`, `chatappFE/src/api/*.service.ts`
- Frontend bootstrap/store handling around auth/session: `chatappFE/src/store/room.store.tsx`, `chatappFE/src/store/friendship.provider.tsx`, notification bootstrap paths
- Tests around route composition and auth-dependent bootstrap behavior
