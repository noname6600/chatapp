# Notification Service

## WebSocket JWT Authentication Flow

The WebSocket endpoint for notifications is:

- `GET /ws/notifications?token=<jwt>`

Authentication is validated during handshake by `AbstractJwtHandshakeInterceptor`:

1. Request must be a servlet request (`SERVLET_NOT_FOUND` on mismatch).
2. Query parameter `token` must exist (`MISSING_TOKEN` if absent).
3. `token` must be non-blank (`INVALID_FORMAT` if blank).
4. Token must decode successfully (`DECODE_FAILED` on decode exception).
5. Decoded token must resolve to non-null user id (`NULL_USER_ID` if null).
6. On success, `userId` is stored in handshake attributes and session is accepted.

The handshake always sets:

- `X-WebSocket-Token-Present: true|false`

On rejection it also sets:

- `X-WebSocket-Rejection-Reason: <REASON>`
- `X-WebSocket-Error-Details: <ExceptionSimpleName>` (for decode failures)

## Rejection Reasons and Troubleshooting

- `MISSING_TOKEN`
  - Cause: `token` query parameter missing.
  - Fix: ensure frontend connects with `?token=<access_token>` and gateway preserves query params.

- `INVALID_FORMAT`
  - Cause: token query value is blank.
  - Fix: verify token retrieval logic before socket connect.

- `DECODE_FAILED`
  - Cause: JWT signature/claims invalid, expired token, wrong issuer/JWKs.
  - Fix: refresh token, verify auth-service JWKS endpoint, verify gateway/service JWT config.

- `NULL_USER_ID`
  - Cause: token decoded but subject cannot be resolved to UUID.
  - Fix: verify token `sub` claim format and decoder mapping.

- `SERVLET_NOT_FOUND`
  - Cause: non-servlet handshake path.
  - Fix: verify service/websocket stack and endpoint registration.

## Metrics

The following Micrometer metrics are emitted:

- `websocket.handshake.success`
  - Counter incremented on each successful handshake.

- `websocket.handshake.rejected{reason=<REASON>}`
  - Counter incremented on each rejected handshake with reason tag.

- `websocket.connections.active`
  - Gauge for currently active notification WebSocket sessions.

### Suggested Alerts

- Handshake rejection surge:
  - Alert if `rate(websocket_handshake_rejected_total[5m])` is above baseline.

- Rejection ratio:
  - Alert if `rejected / (rejected + success)` exceeds threshold (for example 5%) for 10m.

- Active connection drop:
  - Alert on sudden drop of `websocket.connections.active` to near zero during active traffic.

## Local Verification Commands

Run tests:

```bash
./gradlew :notification-service:test
```

Run service:

```bash
./gradlew :notification-service:bootRun
```

Check actuator health:

```bash
curl -i http://localhost:8086/actuator/health
```

Check actuator metrics:

```bash
curl -s "http://localhost:8086/actuator/metrics/websocket.handshake.success"
curl -s "http://localhost:8086/actuator/metrics/websocket.handshake.rejected"
curl -s "http://localhost:8086/actuator/metrics/websocket.connections.active"
```

Test websocket with wscat (valid token):

```bash
wscat -c "ws://localhost:8086/ws/notifications?token=<ACCESS_TOKEN>"
```

Test websocket with missing token:

```bash
wscat -c "ws://localhost:8086/ws/notifications"
```

Test websocket with invalid token:

```bash
wscat -c "ws://localhost:8086/ws/notifications?token=invalid"
```

If connecting through gateway, verify query propagation via:

```bash
wscat -c "ws://localhost:8080/ws/notifications?token=<ACCESS_TOKEN>"
```
