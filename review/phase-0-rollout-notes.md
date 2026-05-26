# Phase 0 Rollout Notes (0.2, 0.3, 0.4)

## Scope

- 0.2 `ddl-auto` hardening with Flyway baseline support for chat-service, user-service, friendship-service, notification-service.
- 0.3 Realtime websocket origin restriction from wildcard to configured allowed origin patterns.
- 0.4 Redis keyspace expiry notification requirement.

## Rollback Notes

### 0.2 Schema Validation and Flyway

- Primary rollback lever: revert `spring.jpa.hibernate.ddl-auto` from `validate` back to `update` only for emergency recovery.
- If Flyway startup introduces unexpected migration-state errors on existing databases, temporarily disable Flyway migration execution in deployment and restore previous release while schema state is reconciled.
- Keep database snapshot before rollout; schema mismatches are expected to fail fast by design.

### 0.3 Websocket Origin Policy

- Primary rollback lever: restore previous websocket allowed origin policy in realtime-edge if legitimate client origins were omitted.
- Safer mitigation before rollback: add missing origin values to `CORS_ALLOWED_ORIGINS` environment configuration and redeploy.

## Non-Compose Redis Requirement

For managed Redis or non-compose deployments, configure key expiry notifications explicitly:

- Required behavior: `notify-keyspace-events` must include expired keyevent flags (`Ex` or broader `KEA`).
- Example command: `CONFIG SET notify-keyspace-events Ex`
- Validation expectation: presence-service startup check should log successful keyspace notification verification.
