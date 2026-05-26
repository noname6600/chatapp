# 13. Dependency Analysis

## Runtime Stack
- Java 21
- Spring Boot 3.5.6
- Spring Cloud 2025.0.1 (gateway/feign-related modules)
- PostgreSQL, Redis, Kafka
- React 19 + Vite frontend

## Service Dependency Highlights
auth-service:
- oauth2-client, jjwt, kafka, websocket starter, JPA

chat-service:
- feign, kafka, redis, JPA, oauth2-resource-server

gateway-service:
- spring-cloud-gateway, resilience4j, reactive redis, oauth2-resource-server

realtime-edge-service:
- both spring-web and spring-webflux + websocket + redis + kafka + security

## Shared Module Dependency Shape
- common-events is foundational for all eventing.
- common-kafka and common-redis expose auto-configured transport abstractions.
- common-web and common-core appear in nearly all services.

## Coupling Risks
1. Widespread dependency on many common modules raises coordinated upgrade cost.
2. realtime-edge includes broad starter set; potential classpath and runtime complexity.
3. Gateway is central critical dependency; policy or version mismatch has cross-system impact.

## Security Dependency Notes
- JWT handled through Spring oauth2 jose and jjwt (auth service signing side).
- verify compatibility and CVE posture regularly (especially cloudinary and kafka clients).

## Frontend Dependencies Relevant to Architecture
- axios + axios-auth-refresh for token lifecycle.
- native WebSocket with custom reconnect/ticket flow.
- Zustand for client-side state stores and reconciliation patterns.

## Build/Test Posture Observations
- tests exist across modules, but some exclusions indicate temporary debt.
- a number of phase/migration docs suggest active architecture evolution.

## Recommendation
Maintain a dependency bill-of-materials review cadence and document an upgrade policy per critical shared library set (Spring, Kafka client, Redis client, JWT libs).
