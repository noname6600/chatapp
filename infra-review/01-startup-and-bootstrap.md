# 01. Startup And Bootstrap

## 1) Repository Bootstrap Topology
The backend is a Gradle multi-project build. Service modules and common modules are declared in chatappBE/settings.gradle.

Common modules loaded into service classpaths:
- common-security
- common-kafka
- common-web
- common-feign
- common-redis
- common-events
- common-redis-cache
- common-core

Service modules:
- auth-service, user-service, chat-service, presence-service, notification-service, friendship-service, upload-service, gateway-service, realtime-edge-service

This matters for startup because every service that depends on common-redis/common-kafka receives those autoconfigured beans automatically if trigger classes are on classpath.

## 2) Boot Entry Points
Each service has a standard SpringApplication.run entry point. Examples:
- auth-service: com.chatweb.auth.AuthServiceApplication
- chat-service: com.chatweb.chat.ChatServiceApplication
- realtime-edge-service: com.chatweb.realtime.RealtimeEdgeApplication
- gateway-service: com.chatweb.gateway.GatewayApplication

Most service applications use ComponentScan with base packages including com.chatweb.common and their domain package. This makes common components visible to each service context.

## 3) Startup Order Inside One Service (Practical Sequence)
Spring startup is concurrent internally, but effective lifecycle sequence is:
1. Environment prepared from application.yaml + env vars.
2. AutoConfiguration imports evaluated.
3. Bean definitions registered.
4. Singleton beans instantiated (unless lazy).
5. PostConstruct and lifecycle callbacks run.
6. Infrastructure endpoints/listener containers started.
7. Scheduler tasks begin once context is ready.

In this codebase, the decisive infrastructure stages are:
- common-redis RedisAutoConfiguration registration
- common-kafka KafkaAutoConfiguration registration
- service-specific SecurityFilterChain and listeners
- RedisMessageListenerContainer in realtime-edge
- Kafka listener containers created from @KafkaListener
- scheduled jobs from @EnableScheduling services

## 4) AutoConfiguration Registration Mechanics
Two core auto-config imports:
- common-redis imports RedisAutoConfiguration
- common-kafka imports KafkaAutoConfiguration

Because they are in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports, they are discovered by Spring Boot without explicit @Import.

### RedisAutoConfiguration creates
- RedisPubSubObserver
- RedisEventRegistry preloaded via SharedEventCatalog.registerAll
- RedisEventSerializer
- RedisEventPublisher
- RedisEventDispatcher
- MessageListener bean for Redis event envelopes

### KafkaAutoConfiguration creates
- KafkaEventObserver
- KafkaEventPublisher backed by KafkaTemplate
- KafkaEventDispatcher

## 5) Service-Specific Bootstrap Highlights

### Gateway
- GatewayApplication enables GatewayReadinessProperties and CorsProperties binding.
- SecurityConfig creates WebFlux Security filter chain.
- GatewayConfig creates CORS filter and Redis rate-limit key resolver.
- Routes, retries, circuit breakers, and websocket route rewrite are loaded from gateway application.yaml.

### Realtime Edge
- RealtimeEdgeApplication enables scheduling.
- WebSocketConfig registers endpoint /realtime and JwtHandshakeInterceptor.
- RedisListenerConfig creates RedisMessageListenerContainer when realtime.redis.listener.enabled=true.
- EdgeSessionMaintenanceJob starts fixed-delay cleanup cycle.
- Kafka listeners in FriendshipKafkaEventConsumer are started.

### Auth
- AuthServiceApplication enables scheduling and configuration properties scan.
- Schedulers start:
  - RefreshTokenCleanupScheduler cron every 30 minutes
  - JwtKeyCleanupScheduler cron hourly
  - KeyManager also has fixedDelay cleanup every 10 minutes
- Security has two chains (oauth2 flow chain and stateless API chain).

### Presence
- PresenceServiceApplication imports common filter stack.
- PresenceRedisKeyspaceNotificationStartupCheck runs on ApplicationRunner and validates Redis notify-keyspace-events setting.
- Presence service relies on TTL state + pub/sub publisher, but explicit Redis key-expiry listener container registration was not found in presence-service source.

## 6) Configuration Loading And Binding
Configuration sources:
- application.yaml per service
- env overrides in docker-compose.local.yml

Important bound properties:
- realtime.session-registry.* controls in-memory vs Redis registry and lease behavior
- realtime.redis.listener.enabled controls Redis pub/sub container in realtime-edge
- notification.kafka.retry.* controls exponential retry config in notification-service
- presence.redis.keyspace-notification.* controls startup safety check in presence-service
- gateway.security.jwt.* controls issuer + skew validation

## 7) Interceptor And Filter Registration
- Servlet stack services use SecurityFilterChain + custom OncePerRequestFilter for internal auth on internal endpoints.
- Gateway uses reactive filter chain and custom JwtAuthFilterGatewayFilterFactory.
- WebSocket handshake interceptor in realtime-edge enforces one-time ticket authentication.
- TraceIdFilter is imported in most services for request correlation ID propagation.

## 8) Kafka Consumer Startup
@KafkaListener methods trigger container creation at startup. Consumers identified include:
- user-service AccountCreatedConsumer
- notification-service message/reaction/friend/account consumers
- chat-service FriendshipBlockEventConsumer
- realtime-edge FriendshipKafkaEventConsumer

Retry strategy is not globally uniform:
- user-service uses DefaultErrorHandler + DeadLetterPublishingRecoverer + FixedBackOff(1s,3)
- notification-service uses DefaultErrorHandler + DeadLetterPublishingRecoverer + ExponentialBackOff
- some services rely on defaults unless custom listener factory is provided

## 9) Redis Startup Behaviors
- Redis connection factory and templates are provided by Spring Data Redis auto config.
- common-redis builds higher-level event publisher/serializer/dispatcher beans.
- realtime-edge explicitly subscribes to redis patterns via RedisMessageListenerContainer.
- Redis keyspace notifications are expected for presence TTL expiry semantics (compose sets redis-server --notify-keyspace-events Kx).

## 10) Runtime Implications Of Startup Design
- Shared autoconfig lowers boilerplate but increases hidden coupling between services and common modules.
- Boot success depends on infra availability assumptions: Redis and Kafka beans may initialize even when downstream brokers are unavailable; failures then surface at first use.
- Multiple cleanup schedulers in auth (hourly and fixed-delay key cleanup paths) require careful operational understanding to avoid duplicate work assumptions.
- Realtime edge startup has critical toggles:
  - disable redis listener -> no pub/sub fanout
  - switch session-registry mode -> local-only behavior vs distributed ownership mode
