# Service Structure Cleanup

Date: May 13, 2026
Scope: service-only structural cleanup and runtime-safe refactor under frozen-common rule

## Runtime blockers fixed first

1. Kafka producer bean mismatch fixed service-locally in:
- auth-service/src/main/java/com/example/auth/configuration/KafkaProducerAdapterConfig.java
- notification-service/src/main/java/com/example/notification/configuration/KafkaProducerAdapterConfig.java
- chat-service/src/main/java/com/example/chat/configuration/KafkaProducerAdapterConfig.java
- friendship-service/src/main/java/com/example/friendship/configuration/KafkaProducerAdapterConfig.java

Resolution:
- Kept common frozen.
- Added service-local adapters from KafkaEventPublisher to KafkaEventProducer.

2. common-websocket BOM import blocker fixed in:
- chatappBE/common/common-websocket/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

Resolution:
- Removed the hidden UTF-8 BOM only.
- This was the only frozen-common exception used in this pass.

3. presence direct websocket disconnect ordering fixed in:
- presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java

Resolution:
- Lifecycle cleanup now runs before session unregister, so offline cleanup still reaches presenceService.offline(...).

4. realtime-edge wrong port property fixed in:
- realtime-edge-service/src/main/resources/application.yaml

Resolution:
- Corrected configuration to use top-level server.port.
- Also repaired the YAML structure so spring.webflux and spring.kafka remain under spring.

5. notification friendship topic mismatch fixed in:
- notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java

Resolution:
- Listener now consumes KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS.

## Structural cleanup applied

### gateway-service

Changes:
- Added a routing comment in gateway-service/src/main/resources/application.yaml clarifying that the current /ws/* routes remain legacy service-local websocket ingress until realtime-edge ingress is explicitly cut over.

Before:
- Legacy websocket routes existed without any statement of posture.

After:
- Gateway config now makes the edge-vs-legacy routing stance explicit without changing runtime behavior.

### auth-service

Changes:
- Tightened SecurityConfig permitAll rules to public auth endpoints only.
- Kept authenticated endpoints under the authenticated path.
- Added a single requirePrincipal helper in AuthController and routed logout-all, change-password, verification-status, and verification-send through it.

Files changed:
- auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java
- auth-service/src/main/java/com/example/auth/controller/AuthController.java

Before:
- All /api/v1/auth/** endpoints were effectively public in security config.
- Several endpoints dereferenced principal directly and could fail as null-principal runtime errors instead of clear unauthorized behavior.

After:
- Public endpoints remain public.
- Authenticated endpoints are now explicit and controller-side guards are consistent.

### user-service

Changes:
- Removed the direct compile-time dependency on upload-service from user-service/build.gradle.
- Replaced UploadAssetMetadata with the local AvatarAssetMetadata boundary model already present in user-service.
- Simplified AccountCreatedConsumer so it delegates to UserKafkaAccountCreatedApplicationService instead of duplicating the same idempotent profile creation logic.
- Updated AccountCreatedConsumerTest to validate delegation.

Files changed:
- user-service/build.gradle
- user-service/src/main/java/com/example/user/service/impl/UserProfileService.java
- user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java
- user-service/src/test/java/com/example/user/kafka/AccountCreatedConsumerTest.java

Before:
- user-service depended directly on upload-service for a contract it already duplicated locally.
- Account creation flow existed twice: once in the Kafka consumer and once in the application service.

After:
- Avatar metadata confirmation is owned by a user-service-local DTO.
- The Kafka consumer is a thin ingress adapter and the application service is the single orchestration path.

### notification-service

Changes:
- Removed the deprecated duplicate NotificationRedisRealtimeAdapter in the earlier blocker cleanup.
- Kept the active path centered on NotificationWebSocketPublisher and the dedicated application services.
- Did not remove NotificationKafkaEventApplicationService because it is still referenced by tests even though it is not the active main-code path.

Before:
- Duplicate realtime adapter created runtime ambiguity and structural noise.

After:
- Runtime path is clearer: active realtime delivery stays with NotificationWebSocketPublisher.

### presence-service

Changes:
- Removed the unreferenced PresenceRealtimeEventPublisher interface.
- Kept PresenceEdgeCommandController and the direct websocket path.

Files changed:
- presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java
- Removed: presence-service/src/main/java/com/example/presence/realtime/publisher/PresenceRealtimeEventPublisher.java

Before:
- Legacy websocket rollback path and edge command ingress coexisted, plus an unused realtime publisher contract.

After:
- Edge command ingress remains clear.
- Legacy websocket path remains available.
- Dead publisher contract is gone.

### chat-service

Changes:
- Removed the unreferenced ChatRealtimeEventPublisher interface.
- Removed six unreferenced room application-service beans that were not wired into controllers, pipelines, or tests:
  - RoomLifecycleApplicationService
  - RoomMembershipApplicationService
  - RoomMessageStateApplicationService
  - RoomMetadataApplicationService
  - RoomModerationApplicationService
  - RoomReadStateApplicationService

Files removed:
- chat-service/src/main/java/com/example/chat/realtime/publisher/ChatRealtimeEventPublisher.java
- chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomLifecycleApplicationService.java
- chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMembershipApplicationService.java
- chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMessageStateApplicationService.java
- chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMetadataApplicationService.java
- chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomModerationApplicationService.java
- chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomReadStateApplicationService.java

Before:
- RoomService was the active path, while a second split-application-service architecture existed in parallel but was unused.

After:
- The inactive second room-service architecture is gone.
- Active room behavior is now easier to locate because RoomService remains the actual implementation surface.

### friendship-service

Changes:
- Removed the unused FriendshipKafkaEventApplicationService.
- Removed the unreferenced FriendshipRealtimeEventPublisher interface.
- Removed the inactive direct websocket outbound delivery path:
  - FriendshipWebSocketPublisher
  - WebSocketFriendshipBroadcaster
- Kept FriendshipWebSocketHandler, FriendshipSessionRegistry, and FriendshipWebSocketConfig as the rollback-compatible websocket endpoint surface.

Files removed:
- friendship-service/src/main/java/com/example/friendship/application/FriendshipKafkaEventApplicationService.java
- friendship-service/src/main/java/com/example/friendship/realtime/publisher/FriendshipRealtimeEventPublisher.java
- friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketPublisher.java
- friendship-service/src/main/java/com/example/friendship/websocket/WebSocketFriendshipBroadcaster.java

Before:
- Command ingress and Kafka publication were the live path, but an unused dispatch-planning service and an inactive websocket outbound publisher path remained in runtime code.

After:
- Live command ingress and Kafka topic model are clearer.
- Rollback websocket ingress remains, but the dead outbound publisher side is removed.

### upload-service

Changes:
- None.

Reason:
- The service was already compact and coherent.
- Cleanup value here was in user-service consuming its contract too directly, not inside upload-service.

### realtime-edge-service

Changes:
- Kept the active com.example.realtime tree as the runtime path.
- Stale com.example.realtimeedge skeleton tree had already been removed in the prior blocker cleanup.
- Repaired application.yaml structure and port property.

Before:
- Active runtime package and stale skeleton package had both existed.

After:
- The runtime path is structurally unambiguous.

## Rollback-safe legacy code intentionally retained

Retained on purpose:
- gateway-service legacy /ws/chat, /ws/presence, /ws/friendship, /ws/notifications routes
- presence-service direct websocket handler path
- friendship-service websocket handler/config/session-registry path
- realtime-edge-service legacy websocket aliases under /ws/*
- chat-service deprecated avatar upload fallback route

Reason:
- These are rollback-compatible compatibility surfaces, not proven dead code.
- They were left intact unless only the inactive outbound side was provably unused.

## Dead or deprecated code removed

Removed as truly dead or structurally misleading:
- notification-service realtime duplicate adapter already removed in prior blocker pass
- realtime-edge-service stale com.example.realtimeedge skeleton tree already removed in prior blocker pass
- chat unused room application-service bean set
- chat unused ChatRealtimeEventPublisher
- presence unused PresenceRealtimeEventPublisher
- friendship unused FriendshipKafkaEventApplicationService
- friendship unused FriendshipRealtimeEventPublisher
- friendship inactive FriendshipWebSocketPublisher
- friendship inactive WebSocketFriendshipBroadcaster

## Services still structurally messy

Still messy, but intentionally left alone in this pass:
- chat-service: RoomService remains large. Cleaning it further would require rewiring active controller and message pipeline dependencies, which is beyond a safe structural pass.
- notification-service: MessageCreatedEventConsumer and ReactionEventConsumer still contain orchestration/business logic directly. NotificationKafkaEventApplicationService remains test-visible but not main-path-active.
- auth-service: DatabaseSchemaFixer still exists. It is runtime debt but removing it safely requires migration-state verification, not a structural-only pass.
- gateway-service: websocket ingress still points to service-local routes rather than realtime-edge. This posture is now documented, but not changed.

## Validation evidence

Compile sweep passed:
- :auth-service:compileJava
- :user-service:compileJava
- :gateway-service:compileJava
- :chat-service:compileJava
- :presence-service:compileJava
- :friendship-service:compileJava
- :notification-service:compileJava
- :upload-service:compileJava
- :realtime-edge-service:compileJava

Focused tests passed:
- :realtime-edge-service:test
- :auth-service:test --tests com.example.auth.controller.AuthControllerTest
- :user-service:test --tests com.example.user.kafka.AccountCreatedConsumerTest
- :user-service:test --tests com.example.user.application.UserKafkaAccountCreatedApplicationServiceTest

Additional test-source validation passed:
- :friendship-service:compileTestJava
- :presence-service:compileTestJava
- :notification-service:compileTestJava

Known unrelated test-source blocker left as-is:
- :chat-service:compileTestJava still fails in stale realtime-contract tests that reference removed or nonexistent common APIs such as com.example.common.integration.realtime.RealtimeContractVersions and com.example.common.kafka.api.*. This predates the current cleanup and was not changed in this pass.
