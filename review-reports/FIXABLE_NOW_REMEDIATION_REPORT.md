# Fixable-Now Remediation Report

Date: 2026-05-21
Scope: Findings from review-reports implemented under change fix-review-reports-fixable-now

## Summary

This pass implemented service-level and runtime fixes that are immediately actionable without broad architecture redesign. Shared common modules were intentionally not modified.

## Fixed Items

1. Removed hardcoded Cloudinary credential fallback values from chat-service runtime configuration.
2. Aligned gateway JWT issuer configuration with auth issuer contract.
3. Fixed gateway websocket rewrite target to realtime-edge handler path.
4. Added gateway verification coverage for issuer mismatch rejection and websocket rewrite contract.
5. Fixed notification DLQ routing to preserve source partition on dead-letter publish.
6. Fixed friendship-service compile issue by importing TraceContext.
7. Aligned notification Redis compose environment keys to SPRING_DATA_REDIS_HOST and SPRING_DATA_REDIS_PORT.
8. Added redis health startup gating for notification-service in compose profiles lacking it.
9. Hardened all backend service container images with non-root runtime user.
10. Added JVM memory sizing defaults in all backend service Dockerfiles.
11. Added per-service .dockerignore files for backend services.

## Verified Existing (No Delta Required)

1. Realtime session registry already applies TTL lease control.
2. Realtime session cleanup already uses cursor scan instead of blocking keyspace lookups.
3. Blocked-pair send step already follows fail-open behavior (prevents false blocked-send during friendship outages).
4. Gateway rate limiting is configured as a default filter and applies to websocket ingress.

## Validation Results

- Executed: gateway-service focused tests + friendship-service compile + notification-service compile.
- Command result: BUILD SUCCESSFUL.

## Remaining Follow-Up

1. Full-stack runtime validation for notification-service logs after compose restart to confirm no localhost Redis attempts.
2. Deferred infrastructure-heavy items from reviews (multi-broker Kafka, Redis HA, Vault/KMS, transactional outbox) to dedicated changes.

## Files Touched

- chatappBE/chat-service/src/main/resources/application.yaml
- chatappBE/gateway-service/src/main/resources/application.yaml
- chatappBE/gateway-service/src/test/java/com/chatweb/gateway/config/SecurityConfigIntegrationTest.java
- chatappBE/gateway-service/src/test/java/com/chatweb/gateway/config/GatewayWebsocketRouteConfigTest.java
- chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java
- chatappBE/friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventProducer.java
- chatappBE/docker-compose.yml
- chatappBE/docker-compose.local.yml
- chatappBE/docker-compose-validation.yml
- chatappBE/docker-compose.phase-b-local.yml
- chatappBE/*/Dockerfile
- chatappBE/*/.dockerignore
