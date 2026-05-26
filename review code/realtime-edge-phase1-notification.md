# Realtime Edge Phase 1 - Notification Migration

Date: 2026-05-12

## Objective

Make notification the first capability migrated to realtime-edge by moving browser websocket ingress/session/delivery responsibility out of notification-service while preserving behavior.

## Scope

In scope:
- chatappBE/notification-service/**
- chatappBE/realtime-edge-service/**
- chatappBE/gateway-service/**

Out of scope respected:
- other services
- chatappBE/common/**
- frontend

## Migration Result Summary

Phase-1 notification migration is in place, with a small residual cleanup completed in this task.

### 1) notification-service is no longer treated as websocket ingress owner

Confirmed in current codebase:
- service-local websocket config/handler/session publisher classes are removed from notification-service
- notification-service now emits realtime events through a port/adapter (`NotificationRealtimePort` -> Redis)

Cleanup completed in this task:
- removed websocket-specific dependencies from notification-service module:
  - removed `project(':common:common-websocket')`
  - removed `spring-boot-starter-websocket`
- removed stale websocket auto-config exclusion from NotificationServiceApplication
- updated notification-service README to reflect domain-only ownership and realtime-edge ingress ownership

### 2) client-facing websocket/session/delivery behavior moved to realtime-edge

Confirmed in realtime-edge-service:
- websocket ingress exists at unified endpoint and migration alias for notification path:
  - `/realtime`
  - `/ws/notifications` (compatibility alias)
- handshake JWT validation is implemented in realtime-edge (`JwtHandshakeInterceptor`)
- session lifecycle ownership is in realtime-edge (`RealtimeSessionRegistry`, `RealtimeWebSocketSessionStore`)
- notification delivery to websocket clients is handled in realtime-edge (`NotificationRealtimeDeliveryService`)
- notification events are consumed from Redis channel pattern `realtime.notification.user.*` and forwarded to connected user sessions (`RedisEventListener`)

Behavior parity preserved:
- notification clients connecting via `/ws/notifications?token=...` continue to work through gateway
- realtime-edge auto-subscribes each authenticated session to `notification:{userId}` to preserve immediate notification delivery without requiring explicit subscribe from the existing client flow

### 3) notification-service now owns only notification domain state and event production

Confirmed:
- domain services/controllers/repositories remain in notification-service
- realtime interaction is outbound event publication only (`NotificationRedisRealtimeAdapter`)

### 4) gateway routing updated accordingly

Confirmed in gateway-service route config:
- `notification-service-ws` route points to realtime-edge-service over websocket
- path predicate remains `/ws/notifications/**` for client compatibility

## Files Updated In This Task

- chatappBE/notification-service/build.gradle
- chatappBE/notification-service/src/main/java/com/example/notification/NotificationServiceApplication.java
- chatappBE/notification-service/README.md

## Validation

Executed from chatappBE root.

Compile validation:
- `./gradlew :notification-service:compileJava :realtime-edge-service:compileJava :gateway-service:compileJava`
- Result: BUILD SUCCESSFUL

Test validation:
- `./gradlew :notification-service:test :realtime-edge-service:test :gateway-service:test`
- Result: BUILD SUCCESSFUL

## Risk Notes

- Current gateway security permits `/ws/**` paths generally; websocket auth enforcement for notification now relies on realtime-edge handshake JWT validation.
- Notification migration is behavior-compatible for existing `/ws/notifications` clients, but this is an interim compatibility path while broader websocket ingress consolidation continues.
