# Realtime Edge Phase 3 - Presence Migration

Date: 2026-05-12

## Objective

Migrate presence browser websocket ingress/session/subscription delivery to realtime-edge while keeping presence domain ownership, state storage, and domain transitions in presence-service.

## Scope

In scope:
- chatappBE/presence-service/**
- chatappBE/realtime-edge-service/**
- chatappBE/gateway-service/**

Out of scope respected:
- chat capability migration
- friendship/notification behavior changes beyond compatibility
- frontend changes

## Migration Summary

Phase-3 presence migration is implemented with realtime-edge owning websocket ingress/delivery and presence-service remaining source-of-truth for presence state and transitions.

### 1) Presence domain ownership preserved in presence-service

Presence-service now provides authenticated command endpoints for websocket-originated presence actions:
- connect
- disconnect
- heartbeat
- room join/leave
- typing/stop-typing

These endpoints delegate to presence domain service methods, so state transitions and storage stay in presence-service (TTL cache + ephemeral room/user state).

Additional domain parity updates:
- disconnect flow now triggers room online-user notifications for rooms the user was in, preserving prior websocket-handler behavior.
- typing/stop-typing remains gated by room membership in domain layer.

### 2) Realtime-edge now owns browser websocket/session/subscription delivery

Realtime-edge changes:
- websocket compatibility alias added: /ws/presence
- handshake interceptor now retains access token for authenticated service-to-service command calls.
- websocket handler supports presence command protocol from browser:
  - presence.user.heartbeat
  - presence.room.join
  - presence.room.leave
  - presence.room.typing
  - presence.room.stop_typing
- presence endpoint sessions auto-subscribe to presence:global and room-derived channels.
- connect flow sends presence.global.snapshot to newly connected presence clients.
- room join flow sends presence.room.snapshot to the joining client.
- disconnect flow triggers presence-service disconnect command when last presence session for a user closes.

### 3) Presence delivery fanout moved to edge

Realtime-edge Redis listener now consumes:
- realtime.presence.user
- realtime.presence.global
- realtime.presence.room.*

Presence delivery service routes events to subscribed websocket sessions:
- global user presence events -> presence:global subscribers
- room events -> room/typing subscribers

Notification and friendship delivery paths remain intact.

### 4) Gateway routing updated

Gateway websocket route for presence is now routed to realtime-edge:
- /ws/presence/** -> realtime-edge-service

### 5) Dependency/ownership cleanup

Presence-service cleanup completed:
- removed common-websocket dependency
- removed spring-boot-starter-websocket dependency
- removed stale websocket auto-config exclusion in PresenceServiceApplication
- added spring-boot-starter-web to keep servlet stack available for security/http context

## Files Updated

- chatappBE/presence-service/build.gradle
- chatappBE/presence-service/src/main/java/com/example/presence/PresenceServiceApplication.java
- chatappBE/presence-service/src/main/java/com/example/presence/service/IPresenceService.java
- chatappBE/presence-service/src/main/java/com/example/presence/service/PresenceService.java
- chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceController.java
- chatappBE/presence-service/src/main/java/com/example/presence/dto/PresenceHeartbeatCommandRequest.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/out/presence/PresenceDomainClient.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/delivery/PresenceRealtimeDeliveryService.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/RedisListenerConfig.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/redis/RedisEventListener.java
- chatappBE/realtime-edge-service/src/main/resources/application.yaml
- chatappBE/gateway-service/src/main/resources/application.yaml

## Validation

Executed from chatappBE root.

Compile validation:
- ./gradlew :presence-service:compileJava :realtime-edge-service:compileJava :gateway-service:compileJava
- Result: BUILD SUCCESSFUL

Test validation:
- ./gradlew :presence-service:test :realtime-edge-service:test :gateway-service:test
- Result: BUILD SUCCESSFUL

## Notes

- Presence migration does not broaden into chat or additional capability cutovers.
- Presence browser command semantics are now edge-ingress + presence-domain execution, matching the target ownership split.
