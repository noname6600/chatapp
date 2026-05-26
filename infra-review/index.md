# Infrastructure Learning Review Index

This document set is a deep, code-linked learning review of infrastructure and communication layers in this repository.

## Reading Path
1. 01-startup-and-bootstrap.md
2. 02-dependency-injection-and-beans.md
3. 03-common-package-analysis.md
4. 04-redis-core.md
5. 05-redis-pubsub-flow.md
6. 06-kafka-core.md
7. 07-event-driven-architecture.md
8. 08-websocket-realtime-flow.md
9. 09-cross-service-communication.md
10. 10-request-to-event-lifecycle.md
11. 11-runtime-sequence-diagrams.md
12. 12-production-behavior.md
13. 13-failure-and-recovery.md
14. 14-scaling-analysis.md
15. 15-learning-notes.md

## Scope
- Runtime bootstrap and bean creation behavior in Spring Boot microservices
- Common infrastructure modules: common-redis, common-kafka, common-events, common-feign, common-web, common-security, common-redis-cache, common-core
- Redis key/value, cache, pub/sub, websocket session ownership, TTL behavior
- Kafka producers, listeners, consumer groups, retry/DLQ behavior
- Event contracts and real producer/consumer map
- Realtime propagation across gateway, realtime-edge, Redis, and WebSocket sessions
- Distributed systems tradeoffs: ordering, durability, replay, dedupe, partial failure

## Source Anchors Used
- Multi-module composition: chatappBE/settings.gradle
- Common autoconfiguration registration:
  - common/common-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - common/common-kafka/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
- Core infra classes:
  - common/common-redis/src/main/java/com/chatweb/common/redis/config/RedisAutoConfiguration.java
  - common/common-kafka/src/main/java/com/chatweb/common/kafka/config/KafkaAutoConfiguration.java
  - common/common-events/src/main/java/com/chatweb/common/event/SharedEventCatalog.java
- Realtime ingress:
  - realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
  - realtime-edge-service/src/main/java/com/chatweb/realtime/config/RedisListenerConfig.java
  - realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/redis/RedisEventListener.java
- Chat event publication path:
  - chat-service/src/main/java/com/chatweb/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStep.java
  - chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java

## How To Use As A Learning Track
- If you are new to distributed systems in Spring: read 01, 02, 03, then 04 and 06.
- If you are building realtime features: read 05, 08, 10, 11, 13, 14.
- If you are preparing for production hardening: read 12, 13, 14, 15.
