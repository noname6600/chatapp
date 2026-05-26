# Realtime Edge Phase 2 - Friendship Migration

Date: 2026-05-12

## Objective

Migrate friendship realtime browser ingress/delivery ownership to realtime-edge, while keeping friendship business rules and domain state logic inside friendship-service.

## Scope

In scope:
- chatappBE/friendship-service/**
- chatappBE/realtime-edge-service/**
- chatappBE/gateway-service/**

Explicitly not broadened:
- chat/presence capability migration
- other services

## Result Summary

Friendship realtime ingress/delivery is now handled by realtime-edge with compatibility routing preserved for existing clients at `/ws/friendship`.

### 1) friendship-service remains business/domain owner

Completed:
- removed websocket ownership residue in friendship-service module setup
- friendship-service continues producing friendship domain events via Kafka (`friendship.events`, `friendship.request.events`)
- no friendship business rule logic was moved out of friendship-service

### 2) realtime-edge now owns friendship browser websocket delivery

Completed in realtime-edge:
- added websocket compatibility alias endpoint `/ws/friendship`
- added endpoint-aware implicit subscription behavior in websocket handler:
  - `/ws/friendship` clients auto-subscribe to `friendship:{userId}`
  - existing notification behavior remains intact for notification/realtime paths
- added friendship delivery service to send websocket payloads to active user sessions
- added friendship Kafka consumers for:
  - `friendship.request.events`
  - `friendship.events`
- preserved friendship websocket message contract expected by frontend:
  - `friendship.request.received`
  - `friendship.request.accepted`
  - `friendship.request.declined`
  - `friendship.request.cancelled`
  - `friendship.status.changed`
- preserved recipient targeting semantics:
  - request sent -> recipient only
  - request accepted/declined/cancelled -> both users
  - status changed events -> both users

### 3) gateway routing cutover

Completed:
- gateway websocket route `/ws/friendship/**` now forwards to realtime-edge-service (phase-2 cutover)

## Files Updated

- chatappBE/friendship-service/build.gradle
- chatappBE/friendship-service/src/main/java/com/example/friendship/FriendshipServiceApplication.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/delivery/FriendshipRealtimeDeliveryService.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/FriendshipKafkaEventConsumer.java
- chatappBE/gateway-service/src/main/resources/application.yaml

## Validation

Executed from chatappBE root.

Compile:
- `./gradlew :friendship-service:compileJava :realtime-edge-service:compileJava :gateway-service:compileJava`
- Result: BUILD SUCCESSFUL

Tests:
- `./gradlew :friendship-service:test :realtime-edge-service:test :gateway-service:test`
- Result: BUILD SUCCESSFUL

## Notes

- Chat and presence routes/ownership were not changed.
- Friendship websocket payloads continue to use `type` + `payload` shape expected by existing frontend socket handling.
