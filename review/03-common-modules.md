# 03. Common Modules

## Overview
Common modules reduce duplication and enforce shared contracts, but they also introduce coupling if abstractions become too broad.

## common-core
Provides:
- shared base exceptions and error codes (`BusinessException`, `CommonErrorCode`)
- lightweight shared primitives

Health:
- healthy, focused, low risk.

## common-web
Provides:
- global error mapping (`GlobalExceptionHandler`)
- trace id propagation (`TraceIdFilter`)
- API response wrappers
- CORS property model

Health:
- healthy.
- risk: central handler can hide service-specific error nuances if overused.

## common-security
Provides:
- JWT helper utilities (`JwtHelper`) and jose dependencies

Health:
- minimal and stable.

## common-feign
Provides:
- Feign trace-id propagation (`FeignTraceConfig`) via `X-Trace-Id`

Health:
- useful for distributed tracing continuity.

## common-events
Provides:
- canonical `EventEnvelope` and `EventMetadata`
- event type enums and payload classes
- `SharedEventCatalog` registration source of truth

Health:
- strong architectural anchor.
- risk: strict contract governance required to avoid payload drift.

## common-kafka
Provides:
- topics constants (`KafkaTopics`)
- generic publisher and dispatcher
- auto-configuration (`KafkaAutoConfiguration`)

Health:
- healthy.
- risk: mixing eventType-as-topic and aggregate topics can confuse operators without strict naming playbooks.

## common-redis
Provides:
- channel constants (`RedisChannels`)
- serializer/registry/listener/publisher abstraction
- auto-configuration (`RedisAutoConfiguration`)

Health:
- healthy for transport concerns.
- risk: pub/sub is non-durable; teams can misread it as durable delivery.

## common-redis-cache
Provides:
- `TimeRedisCacheManager` and failure-aware cache management helpers

Health:
- useful but somewhat heavy abstraction over Spring cache.
- risk: cache availability flags and clear logic require disciplined operational handling.

## Coupling Assessment
Healthy aspects:
- transport and contract concerns centralized.
- service code largely references stable interfaces.

Potential over-coupling:
- common modules include both infra and behavioral assumptions (flow policy references in some paths).
- multiple services depend on many common modules simultaneously, increasing coordinated release pressure.

Boundary leak check:
- no major direct business logic leak in common modules detected.
- strongest protection is `common-events` contract catalog; maintain it rigorously.
