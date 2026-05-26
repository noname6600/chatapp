# 03. Common Package Analysis

## 1) Package Inventory
Common modules under chatappBE/common:
- common-core
- common-events
- common-feign
- common-kafka
- common-redis
- common-redis-cache
- common-security
- common-web

Each module is intentionally transport or cross-cutting infrastructure, not domain logic.

## 1.1) Requested Category Mapping (What Exists In This Repo)
Requested categories vs concrete implementation in this repository:
- config package: service-local configuration classes plus common auto-config modules (common-kafka/common-redis)
- redis package: common-redis plus service adapters in chat/presence/notification/realtime-edge
- kafka package: common-kafka plus service-local consumers/producers
- websocket package: realtime-edge-service websocket/connection/delivery packages
- auth package: common-security helper plus service-local security configs and filters
- event package: common-events contract catalog and integration payloads
- dto package: mostly service-local dto packages; shared transport payloads live in common-events integration package
- constants package: topic/channel constants in KafkaTopics and RedisChannels, plus service-local constants where needed
- security package: per-service SecurityConfig + internal auth filters + gateway reactive security
- logging package: no dedicated common logging module; logging concerns implemented through observer classes and TraceIdFilter
- util package: no large shared util module beyond focused helpers (for example JwtHelper)

Learning implication: the repo favors focused common modules over a giant misc utils/constants package.

## 2) common-events

### Purpose
Canonical shared contract layer for event envelope structure and integration payloads.

### Entrypoints
- EventEnvelope
- EventMetadata
- SharedEventCatalog
- integration/* payload and enum classes

### Initialization
SharedEventCatalog.registerAll is called by common-redis and common-kafka auto-config to preload payload registries.

### Runtime behavior
- Producers attach event metadata with eventId/correlation/source/createdAt.
- Consumers deserialize by eventType lookup in registry.
- Payload-less event types are centrally declared in PAYLOAD_LESS_EVENT_TYPES.

### Scaling implications
Strong contract centralization reduces schema drift, but creates release coupling across services.

## 3) common-redis

### Purpose
Reusable pub/sub framework over StringRedisTemplate with contract validation and typed serialization.

### Entrypoints
- RedisAutoConfiguration
- DefaultRedisEventPublisher
- JsonRedisEventSerializer
- RedisEventDispatcher
- RedisChannels constants

### Initialization
Auto-configured through META-INF auto configuration imports.

### Runtime behavior
- Publisher validates metadata and event naming before convertAndSend.
- Serializer enforces known event types and payload shape.
- Dispatcher maps eventType to RedisEventHandler implementations if service registers them.

### Consumers and producers
- Producers: chat, presence, notification adapters
- Subscribers: realtime-edge RedisMessageListenerContainer + custom RedisEventListener

### Threading
Publish path runs on caller thread. Consume path threading depends on RedisMessageListenerContainer execution model.

### Scaling implications
Pub/sub gives low-latency fanout but no persistence or replay.

## 4) common-kafka

### Purpose
Standardized envelope publishing/dispatch layer on top of KafkaTemplate.

### Entrypoints
- KafkaAutoConfiguration
- DefaultKafkaEventPublisher
- KafkaEventDispatcher
- EventEnvelopeKafkaSerializer/Deserializer
- KafkaTopics

### Runtime behavior
- Publisher validates metadata and event naming.
- Publish is asynchronous via KafkaTemplate.send().whenComplete.
- Deserializer enforces eventType validity and payload presence rules.

### Integration points
Used by auth, friendship, chat, notification services for domain events.

### Scaling implications
Durable, partitioned event transport for eventual consistency and replay-capable recovery.

## 5) common-redis-cache

### Purpose
TTL-aware cache abstraction and manager wrappers around RedisCacheManager.

### Entrypoints
- TimeRedisCacheManager
- TimeRedisCache
- ITimeRedisCacheManager / ITimeRedisCache

### Runtime behavior
- Service-name prefixes keys to avoid collisions.
- Supports explicit per-put TTL semantics.
- Tracks cache availability failure state.

### Coupling risks
Non-standard cache manager behavior requires all services to understand custom semantics (failure queue, prefixed keys).

## 6) common-feign

### Purpose
Cross-service call middleware for trace and JWT relay.

### Entrypoints
- FeignTraceConfig
- FeignJwtConfig

### Runtime behavior
- Injects X-Trace-Id from MDC.
- Relays Authorization bearer token from security context where present.

### Risks
Relaying end-user JWT to downstream services can blur trust boundaries if internal APIs are not carefully segmented.

## 7) common-security

### Purpose
Minimal shared auth helper utilities.

### Entrypoint
- JwtHelper.extractUserId(Jwt)

### Runtime role
Used in controllers and adapters to normalize UUID extraction from JWT subject.

## 8) common-web

### Purpose
HTTP cross-cutting layer for responses, exceptions, CORS, and tracing.

### Entrypoints
- TraceIdFilter
- GlobalExceptionHandler
- CorsProperties
- ApiResponse / ApiError

### Runtime behavior
- Adds or propagates X-Trace-Id per request.
- Standardizes error response format.
- Supports canonical and legacy CORS property prefixes.

### Scaling impact
Uniform API envelope and trace IDs simplify distributed troubleshooting.

## 9) common-core

### Purpose
Generic core primitives: business exception contract and pipeline execution model.

### Entrypoints
- BusinessException/CommonErrorCode
- PipelineExecutor + pipeline descriptors and retry policy

### Runtime behavior
PipelineExecutor executes ordered steps with retry/backoff in-process.

### Concurrency notes
Current executor implementation uses direct call path and Thread.sleep for backoff; no explicit timeout enforcement inside runWithTimeout.

## 10) Centralization Rationale And Tradeoffs

Why centralized:
- shared event contracts
- shared transport wrappers
- consistent trace and exception behavior
- less duplicated framework code

Tradeoffs:
- version coupling across services
- accidental behavior changes from common module upgrades
- hidden startup behavior through auto-config imports

## 11) Package-Level Risk Map
- Highest coupling: common-events, common-kafka, common-redis
- Highest operational sensitivity: common-redis and common-kafka
- Lowest complexity but high usage: common-web, common-security
