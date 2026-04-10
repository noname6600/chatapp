## Why

Browser clients call backend APIs through the gateway, but current behavior can either block preflight requests when gateway CORS is not active or produce duplicate CORS headers when downstream services also emit CORS headers. This causes unstable frontend behavior and prevents a safe production rollout.

## What Changes

- Define a gateway CORS ownership model where the gateway handles browser-facing CORS responses for routed HTTP APIs.
- Add deterministic header deduplication at the gateway so downstream CORS headers do not result in duplicate `Access-Control-*` response headers.
- Make allowed origins environment-driven for production without hardcoding localhost-only values.
- Ensure OPTIONS preflight requests are answered successfully without requiring per-route gateway customizations.
- Keep downstream CORS configurations intact for service-local calls and non-gateway use cases.

## Capabilities

### New Capabilities
- `gateway-edge-cors-coordination`: Defines browser-facing CORS behavior at the gateway, preflight handling, and response header deduplication when downstream services also emit CORS headers.

### Modified Capabilities
- None.

## Impact

- Affected systems: gateway-service runtime configuration and gateway filter chain.
- Affected APIs: all browser-originated HTTP API calls routed through gateway.
- Operational impact: production deploys must provide `CORS_ALLOWED_ORIGINS` with approved origins.
- Risk reduction: eliminates preflight blocking and duplicate CORS headers that break browser enforcement.
