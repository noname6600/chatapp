# 02. Dependency Injection And Beans

## 1) DI Model Used
The codebase uses constructor injection almost everywhere through RequiredArgsConstructor plus component stereotypes.

Infrastructure bean sources are a mix of:
- Spring Boot auto-config beans (Redis, Kafka, Security, Web)
- common module auto-config beans (RedisAutoConfiguration, KafkaAutoConfiguration)
- service-local @Configuration beans
- component-scanned @Component/@Service classes

## 2) Key Bean Graph: Redis Path

### Layer A: low-level transport beans
- RedisConnectionFactory (Spring)
- StringRedisTemplate (Spring, and explicitly in chat RedisAtomicConfig)

### Layer B: common abstraction beans
From RedisAutoConfiguration:
- RedisEventRegistry (DefaultRedisEventRegistry + SharedEventCatalog registrations)
- RedisEventSerializer (JsonRedisEventSerializer)
- RedisEventPublisher (DefaultRedisEventPublisher)
- RedisEventDispatcher
- Redis listener (common listener implementation)

### Layer C: domain adapters
- ChatRedisPublisher
- PresenceRedisPublisher
- RedisNotificationPublisher

### Layer D: listener containers
Service-specific container registration decides whether events are consumed.
- realtime-edge registers RedisMessageListenerContainer and subscribes to realtime.chat.room.*, realtime.notification.user.*, realtime.presence.*

DI insight: common-redis gives publish/deserialize/dispatch primitives; services still own topic subscription and final delivery behavior.

## 3) Key Bean Graph: Kafka Path

### Layer A
- KafkaTemplate
- ConsumerFactory and listener container factory (service-specific when defined)

### Layer B (common-kafka)
- KafkaEventPublisher (DefaultKafkaEventPublisher)
- KafkaEventDispatcher
- KafkaEventObserver

### Layer C (service publishers)
- AccountCreatedEventProducer
- FriendshipEventProducer
- ChatMessageEventPublisherAdapter (publishes to Kafka and Redis)
- NotificationEventProducer

### Layer D (@KafkaListener consumers)
- user AccountCreatedConsumer
- notification MessageCreatedEventConsumer, FriendRequestEventConsumer, others
- chat FriendshipBlockEventConsumer
- realtime-edge FriendshipKafkaEventConsumer

DI insight: producer abstraction is centralized, but consumer error handling is mostly service-specific.

## 4) Bean Conditions And Runtime Mode Switching
Important conditional beans:
- RedisRealtimeSessionRegistry active when realtime.session-registry.mode=redis
- InMemoryRealtimeSessionRegistry active when mode=in-memory or missing
- EdgeSessionMaintenanceJob active unless cleanup-enabled=false
- Redis listener container in realtime-edge active when realtime.redis.listener.enabled=true

This is a runtime profile switch, not compile-time switch.

## 5) Security Bean Model

### Gateway (reactive)
- SecurityWebFilterChain with OAuth2 resource server JWT decoder
- JwtAuthFilterGatewayFilterFactory as route filter

### Service (servlet)
- SecurityFilterChain with oauth2ResourceServer jwt
- Internal service filters on selected services:
  - friendship InternalServiceAuthFilter
  - presence InternalServiceIngressAuthFilter
- auth-service has dual filter chains:
  - oauth2 login chain with IF_REQUIRED session policy
  - API chain with STATELESS policy

DI implication: internal endpoint protection is decentralized across services and depends on per-service property correctness.

## 6) WebSocket Bean Graph In Realtime Edge
- WebSocketConfig registers RealtimeWebSocketHandler and JwtHandshakeInterceptor.
- RealtimeWebSocketHandler depends on:
  - RealtimeSessionRegistry facade
  - RealtimeWebSocketSessionStore
  - ChannelSubscriptionManager
  - PresenceDomainClient + EdgePresenceLifecycleBridge
  - command routers + dispatcher
  - StringRedisTemplate for token refs
  - WebSocketOutboundDeliveryQueue

This is the most dependency-dense bean in the repo and serves as central ingress orchestrator.

## 7) Event Registry Injection Mechanics
SharedEventCatalog registers eventType -> payload mappings into both Redis and Kafka serializers through registries.

Consequences:
- Unknown event type throws early during deserialization.
- Payload-less shared event types are explicitly allowed.
- Contract consistency depends on keeping SharedEventCatalog synchronized with producers.

## 8) Threading And Concurrency At Bean Level
- WebSocketOutboundDeliveryQueue uses fixed worker pool and per-session bounded queue.
- Redis/in-memory registries rely on thread-safe maps or Redis atomic operations.
- Kafka listener threads are managed by Spring Kafka containers.
- Scheduled jobs run on Spring task scheduler.

## 9) Lifecycle Hooks Used
- ApplicationRunner: PresenceRedisKeyspaceNotificationStartupCheck
- @Scheduled: auth cleanup jobs, key manager cleanup, realtime session maintenance
- @PostConstruct: key checks and initialization flows (for example in presence client and some sequence/cache classes)
- TransactionSynchronization.afterCommit wrappers for side effects

## 10) Hidden DI Coupling Risks
- Common module beans activate if trigger class exists, which can create implicit behavior if a service unintentionally adds dependency.
- Some adapters rely on StringRedisTemplate directly while also using higher-level common abstractions.
- Multiple independent internal auth filters can drift in behavior.
- Event producer topic argument usage is inconsistent in places (event-type string used as Kafka topic by design for several events).
