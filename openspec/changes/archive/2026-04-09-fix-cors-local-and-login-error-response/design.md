## Context

The gateway uses Spring WebFlux Security (`spring-cloud-gateway`) with a `CorsWebFilter` bean defined in `GatewayConfig` and Spring Security configured in `SecurityConfig`. The frontend (`http://localhost:5173` local, production domains on VPS) communicates with the gateway at port 8080.

Two independent regressions exist:

1. **CORS preflight blocked by Spring Security**: Spring Security's `SecurityWebFilterChain` runs before the unordered `CorsWebFilter`. When a browser sends `OPTIONS` preflight for any authenticated route (e.g. `/api/v1/users/**`, `/api/v1/chat/**`), Security rejects it with 401 before CORS headers can be written. The fix in `SecurityConfig` already calls `.cors(disable)` to delegate CORS to the filter, but `CorsWebFilter` must also be explicitly ordered before the security chain.

2. **Token-refresh interceptor masks login 401**: `createBaseApi` (used by all API instances including `authApi`) installs `createAuthRefreshInterceptor`. Every 401 response triggers `refreshAuthLogic`, which checks for a refresh token in localStorage. When a user submits wrong credentials, the login endpoint returns 401 — the interceptor catches it, finds no refresh token, and rejects with the string `"No refresh token"`. The original backend error message (`"Invalid credentials"`) is never seen. Additionally, `extractErrorMessage` does not read the backend `message` field from AxiosError responses, so legitimate 401 messages from other endpoints are also swallowed.

## Goals / Non-Goals

**Goals:**
- Ensure all routes (authenticated and public) return correct CORS headers on OPTIONS preflight
- Ensure login/register with wrong credentials shows the backend's error message in the UI
- Ensure `extractErrorMessage` returns the server-side message string for any AxiosError

**Non-Goals:**
- Changing the authentication mechanism or token lifecycle
- Changing CORS allowed origins values or environment variable contract
- Server-side error message copy or i18n

## Decisions

### Decision 1 — Filter ordering via `@Order(Ordered.HIGHEST_PRECEDENCE)`

**Chosen**: Add `@Order(Ordered.HIGHEST_PRECEDENCE)` to the `CorsWebFilter` bean in `GatewayConfig.java`.

**Alternative A**: Add `.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()` in `SecurityConfig`. This also lets preflights through, but requires Security awareness of the CORS role, creating two places where CORS behavior is implicitly controlled.

**Alternative B**: Use `.cors(cors -> cors.configurationSource(corsConfigurationSource()))` in Security instead of `.cors(disable)`. This ties CORS into the Security chain ordering, which is less transparent in reactive WebFlux and harder to test independently.

**Rationale**: `Ordered.HIGHEST_PRECEDENCE` is the standard Spring WebFlux recommendation for `CorsWebFilter`. It is a single-line change, self-documenting, and requires no change to `SecurityConfig`.

### Decision 2 — `shouldRefresh` guard on auth endpoints

**Chosen**: Pass a `shouldRefresh` callback to `createAuthRefreshInterceptor` that returns `false` when the failing request URL includes `/login` or `/register`.

**Alternative**: Create a separate bare axios instance for auth endpoints with no refresh logic. This is cleaner structurally but would require extracting auth-specific axios creation and would diverge from the other API modules.

**Rationale**: `axios-auth-refresh` ships `shouldRefresh` specifically for this use case. One-line change, keeps auth and other API configurations consistent.

### Decision 3 — AxiosError message extraction in `extractErrorMessage`

**Chosen**: Check `axios.isAxiosError(error)` first and return `error.response?.data?.message ?? error.message`.

**Rationale**: All Spring Boot services return a JSON body with a `message` field on errors. The current `extractErrorMessage` only handles plain `Error` and string, causing AxiosError to fall through to `error.message` which returns the generic Axios string. This fix ensures the backend message reaches the UI across all API calls — not only login.

## Risks / Trade-offs

- [Risk] `shouldRefresh` skipping `/login` and `/register` means a genuine 401 from those endpoints (e.g. expired session on a protected page that happens to POST to a similar URL) would not be refreshed. → Mitigation: The auth endpoints are public (no token needed); a 401 from them always means bad credentials or misuse, so skipping refresh is correct.
- [Risk] `Ordered.HIGHEST_PRECEDENCE` may interact with other high-precedence filters if they are added later. → Mitigation: CORS handling is gateway-wide cross-cutting; it being first is always correct. Document in `GatewayConfig`.
- [Risk] `extractErrorMessage` now relies on `error.response?.data?.message`. If a backend returns a non-standard error body the field will be undefined. → Mitigation: falls back to `error.message` (Axios generic), still better than before.

## Migration Plan

1. Apply backend change: add `@Order` import and annotation to `CorsWebFilter` bean.
2. Apply frontend changes: update `base.api.ts` interceptor options, update `error.ts` extractor.
3. Rebuild and restart local stack (`docker compose --env-file .env.local -f docker-compose.local.yml up -d --build`).
4. Verify: open browser devtools on the FE app, attempt login with wrong credentials — should see "Invalid credentials"; verify no CORS errors on any authenticated API call.

## Open Questions

None. All decisions are resolved.
