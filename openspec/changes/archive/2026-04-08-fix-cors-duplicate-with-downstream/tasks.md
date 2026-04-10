## 1. Gateway CORS Configuration

- [x] 1.1 Ensure gateway CORS reads allowed origins from `gateway.cors.allowed-origins` with `CORS_ALLOWED_ORIGINS` environment override.
- [x] 1.2 Ensure preflight is handled by gateway edge CORS before auth rejection paths.
- [x] 1.3 Validate configured origins are parsed from comma-separated values and blank entries are ignored.

## 2. Header Deduplication

- [x] 2.1 Add gateway response header deduplication for `Access-Control-Allow-Origin`.
- [x] 2.2 Add gateway response header deduplication for `Access-Control-Allow-Credentials`.
- [x] 2.3 Verify deduplication strategy keeps a single effective header value in proxied responses.

## 3. Compatibility with Downstream CORS

- [x] 3.1 Confirm downstream service CORS configurations remain unchanged.
- [x] 3.2 Validate browser requests through gateway do not fail due to duplicated CORS headers when downstream also sets CORS headers.

## 4. Verification and Rollout

- [x] 4.1 Add or update gateway integration tests for allowed-origin preflight success and disallowed-origin behavior.
- [x] 4.2 Add or update tests for duplicate CORS header collapse in gateway responses.
- [x] 4.3 Execute smoke checks for login flow from approved frontend origin via gateway.
- [x] 4.4 Document production deployment requirement to set `CORS_ALLOWED_ORIGINS` to approved frontend domains.
