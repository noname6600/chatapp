## Why

Two regressions block local development: (1) all authenticated API routes return CORS errors because Spring Security intercepts OPTIONS preflight requests before the `CorsWebFilter` can inject the required headers, and (2) submitting wrong login credentials shows "No refresh token" instead of "Invalid credentials" because the axios-auth-refresh interceptor silently hijacks every 401 — including intentional ones from the login endpoint.

## What Changes

- **Gateway** (`GatewayConfig.java`): Annotate the `CorsWebFilter` bean with `@Order(Ordered.HIGHEST_PRECEDENCE)` so it runs before Spring Security and handles preflight OPTIONS on all routes, including authenticated ones.
- **Frontend** (`base.api.ts`): Add a `shouldRefresh` guard to `createAuthRefreshInterceptor` that returns `false` for auth endpoints (`/login`, `/register`, `/refresh`), preventing the interceptor from swallowing their intentional 401/error responses.
- **Frontend** (`error.ts`): Extend `extractErrorMessage` to handle `AxiosError` and extract the backend `message` field from `error.response.data`, so server error messages ("Invalid credentials", "Account disabled") reach the UI as-is rather than as the generic Axios "Request failed with status code 401".

## Capabilities

### New Capabilities

- `fe-auth-error-passthrough`: Frontend correctly extracts and surfaces backend auth error messages (invalid credentials, account disabled) without the token-refresh interceptor masking them.

### Modified Capabilities

- `gateway-cors`: CorsWebFilter must be ordered before Spring Security to respond to OPTIONS preflight on authenticated routes; the current unordered bean allows Security to reject preflights before CORS headers are written.

## Impact

- `chatappBE/gateway-service/src/main/java/com/example/gateway/config/GatewayConfig.java` — `CorsWebFilter` bean ordering
- `chatappFE/src/api/base.api.ts` — auth-refresh interceptor skip logic
- `chatappFE/src/utils/error.ts` — AxiosError message extraction
- No API contracts, environment variables, or other services are affected
