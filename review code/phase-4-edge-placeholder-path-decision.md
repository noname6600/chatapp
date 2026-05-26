# Phase 4 - Realtime Edge Placeholder Path Decision

Date: 2026-05-14
Execution source of truth: review code/service-fix-plan.md
Scope: realtime-edge-service only

## Goal
Resolve misleading placeholder state of generic Kafka/event delivery paths so the service truthfully reflects what is production-ready.

## Decision
Chose the safe quarantine/remove path.

Rationale:
- The generic KafkaEventConsumer path was placeholder-only and explicitly disabled at listener level (autoStartup=false).
- Its paired generic EventDeliveryService/EventRouter path contained placeholder routing and placeholder websocket send behavior.
- Keeping these classes in-tree as active Spring components created false architectural signals about supported delivery capabilities.
- Service already has concrete, active delivery ownership for friendship events via FriendshipKafkaEventConsumer and dedicated delivery services.

## Implemented Changes

Removed placeholder generic path components:
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/delivery/EventDeliveryService.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/delivery/EventRouter.java

## Outcome
- No half-enabled generic Kafka/event delivery placeholder remains.
- Realtime-edge now more honestly represents supported behavior: explicit domain consumers/delivery flows instead of generic unfinished scaffolding.
- No gateway websocket cutover changes introduced.
- No event platform redesign introduced.

## Verification Gate
Most relevant service-local gate for affected scope:
- Command: ./gradlew :realtime-edge-service:test
- Result: BUILD SUCCESSFUL
