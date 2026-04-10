## Why

Service-to-Redis connectivity is inconsistent across local Docker Compose services, which allows some containers to inherit localhost defaults and fail at runtime even while Redis is healthy. Aligning Redis environment contracts across all services prevents config drift and removes avoidable startup/runtime incidents.

## What Changes

- Standardize Redis-related environment variables in local compose so every Redis-dependent service uses explicit container-network settings.
- Add explicit Redis host/port configuration for services that currently rely on framework defaults.
- Align service dependency declarations (`depends_on` with Redis health) where Redis is a required runtime dependency.
- Define a single compose-level convention for Redis connectivity keys to keep future services consistent.
- Validate by restarting the full stack and checking startup/runtime logs for Redis connection errors.

## Capabilities

### New Capabilities
- `local-compose-redis-config-consistency`: Defines required Redis configuration and dependency conventions for Docker Compose services in local environment.

### Modified Capabilities
- None.

## Impact

- Affected infrastructure/config: [chatappBE/docker-compose.local.yml](chatappBE/docker-compose.local.yml) and Redis-related service environment contracts.
- Affected backend services: all Redis-dependent services (including user-service and other async/realtime services).
- Operational impact: fewer runtime `RedisConnectionFailureException` incidents from misconfigured localhost defaults.
- Verification impact: requires compose-wide restart and service log validation after config sync.
