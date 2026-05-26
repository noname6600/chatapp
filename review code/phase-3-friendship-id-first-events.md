# Phase 3 - Friendship ID-First Durable Events

Date: 2026-05-14
Execution source of truth: review code/service-fix-plan.md
Scope: friendship-service only

## Objective
Remove synchronous user-service display-name enrichment from friendship durable event production while keeping event contracts stable and useful.

## Implemented Changes

1. Removed synchronous user-service dependency from friendship event producer
- Updated FriendshipEventProducer to remove UserClient injection and all user-service lookup logic.
- Deleted resolveSenderDisplayName(...) network enrichment path.

2. Kept durable event semantics stable and ID-first
- Friend request lifecycle events still emit FriendRequestPayload on friendship request aggregate topic.
- Friendship status events still emit FriendshipPayload on friendship aggregate topic.
- For FriendRequestPayload.senderDisplayName, producer now uses deterministic ID-first fallback (senderId.toString()) without external calls.
- Core relationship facts remain account IDs and relationship state.

3. Added focused producer contract test coverage
- Updated FriendshipEventProducerTopicContractTest for constructor/dependency changes.
- Added test that validates friend request payload carries senderId and senderDisplayName derived from sender account ID, confirming no synchronous enrichment requirement in producer logic.

## Files Changed
- chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java
- chatappBE/friendship-service/src/test/java/com/example/friendship/kafka/FriendshipEventProducerTopicContractTest.java

## Verification Gate
Most relevant service-local gate for affected scope:
- Command: ./gradlew :friendship-service:test
- Result: BUILD SUCCESSFUL

## Safety Notes
- Change is narrow and local to friendship-service.
- No consumer ecosystem redesign.
- Durable events are now producer-side independent of user-service availability for display-name enrichment.
- Downstream read-model/notification layers can enrich display labels if needed.
