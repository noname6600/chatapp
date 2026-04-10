# local-compose-redis-config-consistency Specification

## Purpose
Defines required Redis configuration and dependency conventions for Docker Compose services in the local development environment.

## Requirements

### Requirement: Local Redis-dependent services SHALL declare explicit Redis endpoint configuration
Every service in local Docker Compose that depends on Redis for runtime behavior SHALL explicitly set Redis endpoint environment configuration to the compose service address.

#### Scenario: Redis-dependent service starts with explicit endpoint
- **WHEN** a Redis-dependent service starts in local compose
- **THEN** its environment includes `SPRING_DATA_REDIS_HOST=redis`
- **AND** its environment includes `SPRING_DATA_REDIS_PORT=6379`

### Requirement: Local Redis-dependent services SHALL gate startup on Redis health
Every Redis-dependent service in local Docker Compose SHALL declare Redis as a health-checked startup dependency.

#### Scenario: Redis startup gating is enforced
- **WHEN** local compose starts Redis-dependent services
- **THEN** those services wait for Redis health check success before startup

### Requirement: Local full-stack restart SHALL not produce localhost Redis connection attempts
After synchronization of compose Redis configuration, Redis-dependent services SHALL not attempt Redis connections to localhost during normal startup and runtime cache access paths.

#### Scenario: Runtime log validation after restart
- **WHEN** the local stack is restarted with synchronized compose configuration
- **THEN** service logs do not contain Redis connection attempts to `localhost:6379`
- **AND** Redis-dependent request paths execute without Redis host resolution failures
