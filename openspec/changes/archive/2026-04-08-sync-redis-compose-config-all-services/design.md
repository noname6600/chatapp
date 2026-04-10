## Context

Redis is healthy in local Docker Compose, but Redis-dependent service configuration is inconsistent across services. Some services declare explicit Redis host/port and Redis health dependency, while others can fall back to framework defaults (localhost), causing runtime connection failures inside containers.

This change is cross-cutting because it affects compose contracts used by multiple backend services and local developer bootstrap behavior.

## Goals / Non-Goals

**Goals:**
- Define one consistent Redis connectivity contract for local compose services.
- Ensure every Redis-dependent service uses explicit container-network Redis endpoint configuration.
- Align startup ordering for Redis-dependent services using Redis health dependencies.
- Provide deterministic verification after full-stack restart.

**Non-Goals:**
- Introducing Redis authentication/TLS changes.
- Refactoring service business logic unrelated to infrastructure config.
- Changing production deployment topology or non-local compose behavior.

## Decisions

### Decision 1: Standardize on explicit Spring Redis env keys for service containers
Use `SPRING_DATA_REDIS_HOST=redis` and `SPRING_DATA_REDIS_PORT=6379` in each Redis-dependent service environment block.

Rationale:
- Removes reliance on defaults that resolve to localhost inside containers.
- Matches existing Spring Boot binding used by current services.

Alternatives considered:
- Keep per-service custom key names and map in application YAML: rejected due to drift risk and inconsistent conventions.
- Use localhost with host networking: rejected because it breaks portability and compose network isolation.

### Decision 2: Enforce Redis health dependency for Redis-dependent services
Add `depends_on` Redis with `condition: service_healthy` for services that must talk to Redis at runtime.

Rationale:
- Reduces startup race failures during local bootstrap.

Alternatives considered:
- No dependency gate and rely on retry only: rejected due to noisy startup failures and inconsistent first-run behavior.

### Decision 3: Keep scope to local compose contract synchronization
Implement changes in local compose and verify runtime behavior via restart + logs.

Rationale:
- Solves observed local issue quickly with low risk.
- Allows follow-up parity work for other compose files if needed.

Alternatives considered:
- Simultaneous refactor of all compose variants and service YAML defaults: rejected for this change due to broader blast radius.

## Risks / Trade-offs

- [Risk] Missing a Redis-dependent service in compose update leaves hidden inconsistency. -> Mitigation: enumerate all services and validate env blocks after edit.
- [Risk] Added `depends_on` can slightly increase startup time. -> Mitigation: acceptable trade-off for deterministic bootstrap.
- [Risk] Service image/runtime could still override env values unexpectedly. -> Mitigation: validate effective environment and logs after restart.

## Migration Plan

1. Identify all Redis-dependent services in local compose.
2. Add/normalize Redis host/port environment keys for each one.
3. Add/normalize Redis health dependency where required.
4. Restart full local stack.
5. Validate Redis connectivity from service logs and targeted smoke calls.

Rollback:
- Revert compose changes and restart stack; no schema/data migration required.

## Open Questions

- Should the same Redis contract synchronization be applied immediately to [chatappBE/docker-compose.yml](chatappBE/docker-compose.yml) in this change or in a separate parity change?
- Should gateway retain `REDIS_HOST/REDIS_PORT` only, or also set Spring-form keys for stricter uniformity if future internals change?
