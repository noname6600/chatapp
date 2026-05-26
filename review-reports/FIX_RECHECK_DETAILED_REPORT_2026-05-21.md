# Fix Recheck Detailed Report

Date: 2026-05-21
Scope: Recheck of fixes already applied so far across the current review-driven implementation passes.
Method: Verified current code/config/doc state against the previously recorded fixes in:
- PHASE1_RUN_NOTE_2026-05-21.md
- PHASE2_RUN_NOTE_2026-05-21.md
- PHASE3_RUN_NOTE_2026-05-21.md
- DETAILED_FIX_BACKLOG_FROM_ALL_REPORTS.md (`Already fixed in current change` section)

This report answers two questions for each item:
1. Is the fix still visibly present in the current workspace?
2. Why was this specific change the right implementation choice?

## Overall Result

- Total items rechecked: 24
- Present and still fixed: 23
- Intentionally partial: 1
- Missing/regressed fixes found in this recheck: 0

The only intentionally partial item remains INFRA-003, where visibility was added through health reporting, but hard fail-fast behavior was deliberately deferred to avoid breaking environments that intentionally disable the listener.

## A. Backlog Snapshot Items Already Fixed Earlier In This Change

### 1. Cloudinary secret fallback removal
- Status: PRESENT AND FIXED.
- Current evidence:
  - chat-service Cloudinary config no longer uses fallback defaults in [chatappBE/chat-service/src/main/resources/application.yaml](d:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/resources/application.yaml).
- Why this change makes sense:
  - Secrets and external service identifiers should fail closed, not silently fall back, otherwise production can start with unintended credentials or environment leakage.

### 2. Gateway websocket rewrite to realtime handler contract
- Status: PRESENT AND FIXED.
- Current evidence:
  - Gateway route rewrites websocket traffic to realtime-edge contract in [chatappBE/gateway-service/src/main/resources/application.yaml](d:/Work/PET/chatappPET/chatapp/chatappBE/gateway-service/src/main/resources/application.yaml).
  - Route coverage test exists in [chatappBE/gateway-service/src/test/java/com/chatweb/gateway/config/GatewayWebsocketRouteConfigTest.java](d:/Work/PET/chatappPET/chatapp/chatappBE/gateway-service/src/test/java/com/chatweb/gateway/config/GatewayWebsocketRouteConfigTest.java).
- Why this change makes sense:
  - The gateway should normalize the external websocket path to the internal edge contract so clients stay stable while backend routing remains explicit and testable.

### 3. Friendship compile blocker from missing TraceContext import
- Status: PRESENT AND FIXED.
- Current evidence:
  - TraceContext is imported and used in [chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java](d:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java).
- Why this change makes sense:
  - Correlation propagation is infrastructure glue; fixing the import at the actual publisher point restores compile correctness and preserves cross-service observability.

### 4. Notification compose Redis key alignment
- Status: PRESENT AND FIXED.
- Current evidence:
  - notification-service reads standard Spring Redis env vars in [chatappBE/notification-service/src/main/resources/application.yaml](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/resources/application.yaml).
- Why this change makes sense:
  - Using framework-standard variable names reduces compose drift and avoids per-service configuration exceptions.

### 5. Notification DLQ partition routing correction
- Status: PRESENT AND FIXED.
- Current evidence:
  - Kafka DLQ routing preserves source partition in [chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java).
- Why this change makes sense:
  - Preserving partition keeps failure behavior closer to source ordering and makes replay/debugging operationally simpler.

### 6. Gateway issuer configuration alignment
- Status: PRESENT AND FIXED.
- Current evidence:
  - Gateway issuer config and validator align in:
  - [chatappBE/gateway-service/src/main/resources/application.yaml](d:/Work/PET/chatappPET/chatapp/chatappBE/gateway-service/src/main/resources/application.yaml)
  - [chatappBE/gateway-service/src/main/java/com/chatweb/gateway/config/SecurityConfig.java](d:/Work/PET/chatappPET/chatapp/chatappBE/gateway-service/src/main/java/com/chatweb/gateway/config/SecurityConfig.java)
- Why this change makes sense:
  - If the configured issuer and the actual validation issuer differ, valid tokens can be rejected or invalid tokens accepted; alignment closes that class of auth misconfiguration.

### 7. Container hardening with non-root user and JVM memory flags
- Status: PRESENT AND FIXED.
- Current evidence:
  - All 9 service Dockerfiles exist and use non-root user plus JVM RAM percentage flags, for example:
  - [chatappBE/auth-service/Dockerfile](d:/Work/PET/chatappPET/chatapp/chatappBE/auth-service/Dockerfile)
  - [chatappBE/notification-service/Dockerfile](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/Dockerfile)
- Why this change makes sense:
  - Non-root execution is a baseline container hardening control, and explicit JVM memory percentages avoid container-unaware heap sizing.

### 8. Per-service .dockerignore files
- Status: PRESENT AND FIXED.
- Current evidence:
  - Root and per-service .dockerignore files are present across backend services.
- Why this change makes sense:
  - Docker context control is cheap performance hardening: smaller contexts mean faster builds and fewer accidental artifacts inside images.

## B. Phase 1 Rechecked Fixes

### 1. SEC-001 token stored in websocket session attributes
- Status: PRESENT AND FIXED.
- Current evidence:
  - Handshake stores only `accessTokenRef` instead of raw token in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java).
  - Handler resolves token by Redis reference and clears it on disconnect in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java).
- Why this change makes sense:
  - Session attributes are long-lived in-memory structures, so moving the sensitive token to short-TTL Redis storage minimizes memory exposure while keeping the runtime behavior compatible.

### 2. DB-002 room owner transfer race
- Status: PRESENT AND FIXED.
- Current evidence:
  - Pessimistic lock repository methods exist in [chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/room/repository/RoomMemberRepository.java](d:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/room/repository/RoomMemberRepository.java).
  - Leave/owner handoff uses the locked path in [chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/room/service/impl/RoomService.java](d:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/room/service/impl/RoomService.java).
- Why this change makes sense:
  - Ownership is an invariant, so the correct fix is to serialize the decision point with row locks instead of layering retries on an inherently racy read-modify-write flow.

### 3. GATEWAY-001 readiness missing realtime-edge
- Status: PRESENT AND FIXED.
- Current evidence:
  - realtime-edge is included in gateway required-services in [chatappBE/gateway-service/src/main/resources/application.yaml](d:/Work/PET/chatappPET/chatapp/chatappBE/gateway-service/src/main/resources/application.yaml).
- Why this change makes sense:
  - A gateway that routes websocket traffic must not report ready when its websocket downstream is absent; readiness should reflect actual dependency usability.

### 4. ARCH-002 realtime-edge BOM drift
- Status: PRESENT AND FIXED.
- Current evidence:
  - realtime-edge BOM is aligned in [chatappBE/realtime-edge-service/build.gradle](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/build.gradle).
- Why this change makes sense:
  - BOM drift creates hidden transitive dependency skew; the build file is the right single-point fix for platform alignment.

### 5. KAFKA-005 invalid eventId bypass in notification dedupe
- Status: PRESENT AND FIXED.
- Current evidence:
  - Dedupe guard rejects null/blank/non-UUID IDs in [chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationEventDedupeGuard.java](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationEventDedupeGuard.java).
  - Application service logs invalid ID situations in [chatappBE/notification-service/src/main/java/com/chatweb/notification/application/NotificationKafkaEventApplicationService.java](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/chatweb/notification/application/NotificationKafkaEventApplicationService.java).
- Why this change makes sense:
  - Invalid IDs should fail safe, because allowing malformed IDs through a dedupe boundary effectively lets duplicates bypass the protection mechanism.

### 6. SEC-004 blank prepare-token secret
- Status: PRESENT AND FIXED.
- Current evidence:
  - Startup validation exists in [chatappBE/upload-service/src/main/java/com/chatweb/upload/service/UploadSigningService.java](d:/Work/PET/chatappPET/chatapp/chatappBE/upload-service/src/main/java/com/chatweb/upload/service/UploadSigningService.java).
- Why this change makes sense:
  - Secrets required for token signing should be validated at startup, not discovered after insecure runtime behavior has already begun.

### 7. INFRA-001 Redis keyspace events mismatch between compose profiles
- Status: PRESENT AND FIXED.
- Current evidence:
  - Local compose Redis command now includes notify-keyspace-events in [chatappBE/docker-compose.local.yml](d:/Work/PET/chatappPET/chatapp/chatappBE/docker-compose.local.yml).
- Why this change makes sense:
  - When local and main compose profiles differ on Redis notification behavior, bugs appear environment-specific; aligning startup flags removes that class of drift.

### 8. INFRA-003 listener enablement visibility/enforcement
- Status: PRESENT AND INTENTIONALLY PARTIAL.
- Current evidence:
  - Health indicator exposes disabled-listener state in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/config/RedisListenerHealthIndicator.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/config/RedisListenerHealthIndicator.java).
- Why this change makes sense:
  - Health visibility was the low-risk operational improvement; hard fail-fast was deferred because some test and boot flows intentionally disable the listener.
- Note:
  - This was not left unfinished by accident; it was a deliberate safety tradeoff.

### 9. ARCH-003 media-service vs upload-service naming drift
- Status: PRESENT AND FIXED AS DOCUMENTATION CANONICALIZATION.
- Current evidence:
  - Canonical naming note exists in [review-reports/SERVICE_NAMING_CANONICAL.md](d:/Work/PET/chatappPET/chatapp/review-reports/SERVICE_NAMING_CANONICAL.md).
- Why this change makes sense:
  - This was a terminology problem, not a runtime defect, so documenting the canonical name is the smallest correct fix without risking unrelated code churn.

## C. Phase 2 Rechecked Fixes

### 1. KAFKA-002 durable dedupe for friendship flows
- Status: PRESENT AND FIXED.
- Current evidence:
  - Redis-backed dedupe exists in:
  - [chatappBE/friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventDedupeGuard.java](d:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventDedupeGuard.java)
  - [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/kafka/FriendshipEventDedupeGuard.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/kafka/FriendshipEventDedupeGuard.java)
- Why this change makes sense:
  - Dedupe state must survive rebalance and restart, so moving from in-memory maps to Redis TTL keys fixes the actual durability boundary.

### 2. KAFKA-003 retry/backoff policy in notification-service
- Status: PRESENT AND FIXED.
- Current evidence:
  - Exponential backoff configuration is in [chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java).
  - Matching tuning properties are in [chatappBE/notification-service/src/main/resources/application.yaml](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/resources/application.yaml).
- Why this change makes sense:
  - Fixed small backoff is too rigid for consumer outages; exponential tuning gives safer load behavior under transient broker or downstream failure.

### 3. REDIS-001 room membership auth cache invalidation
- Status: PRESENT AND FIXED.
- Current evidence:
  - TTL reduction and targeted invalidation method exist in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/subscription/ChannelSubscriptionManager.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/subscription/ChannelSubscriptionManager.java).
  - Membership-event wiring exists in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/redis/RedisEventListener.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/redis/RedisEventListener.java).
- Why this change makes sense:
  - Shorter TTL helps, but event-driven invalidation is the real fix because authorization cache correctness should follow membership changes quickly, not wait for expiry.

### 4. REALTIME-001 blocking side effects in websocket path
- Status: PRESENT AND FIXED.
- Current evidence:
  - Async side-effect executor exists in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeSideEffectQueue.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeSideEffectQueue.java).
  - Handler submits presence operations to that queue in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java).
- Why this change makes sense:
  - Websocket ingress should stay CPU-light and non-blocking; pushing remote side effects to a bounded queue preserves responsiveness and isolates slow downstream calls.

### 5. REALTIME-002 synchronized send lock on websocket sessions
- Status: PRESENT AND FIXED.
- Current evidence:
  - Queue-based outbound delivery exists in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/WebSocketOutboundDeliveryQueue.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/WebSocketOutboundDeliveryQueue.java).
  - Remaining delivery flows use enqueue-based send in:
  - [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/PresenceRealtimeDeliveryService.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/PresenceRealtimeDeliveryService.java)
  - [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/FriendshipRealtimeDeliveryService.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/FriendshipRealtimeDeliveryService.java)
  - [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/NotificationRealtimeDeliveryService.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/NotificationRealtimeDeliveryService.java)
  - [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/dispatch/EdgeDeliveryHandoffListener.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/dispatch/EdgeDeliveryHandoffListener.java)
- Why this change makes sense:
  - Per-session outbound queues remove global lock-style contention and are the right throughput-oriented replacement for direct synchronized sends.

## D. Phase 3 Rechecked Fixes

### 1. SEC-002 mid-session token expiry enforcement
- Status: PRESENT AND FIXED.
- Current evidence:
  - Active-token enforcement and disconnect path exist in [chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java).
  - Regression test exists in [chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandlerTokenExpiryTest.java](d:/Work/PET/chatappPET/chatapp/chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandlerTokenExpiryTest.java).
- Why this change makes sense:
  - Long-lived websocket sessions need their own expiry enforcement; otherwise a token only matters at connect time and becomes effectively over-trusted afterward.

### 2. KAFKA-004 hardcoded topic/group literals
- Status: PRESENT AND FIXED FOR THE CONFIRMED TOUCHED OCCURRENCES.
- Current evidence:
  - user-service account-created consumer now uses shared topic constant in [chatappBE/user-service/src/main/java/com/chatweb/user/kafka/AccountCreatedConsumer.java](d:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/chatweb/user/kafka/AccountCreatedConsumer.java).
  - notification-service consumer group is centralized in [chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationKafkaConsumerGroups.java](d:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationKafkaConsumerGroups.java).
  - chat friendship consumer uses class constant in [chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/kafka/FriendshipBlockEventConsumer.java](d:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/kafka/FriendshipBlockEventConsumer.java).
- Why this change makes sense:
  - Transport names and consumer groups are coordination identifiers; centralizing them reduces typo risk and makes future topic/group renames local rather than repo-wide manual edits.
- Note:
  - This pass fixed the confirmed literal occurrences we targeted; it was not a repo-wide literal audit.

## Recheck Conclusion

No fix regressions were found in the items already implemented.

The fixes that were applied generally followed the right pattern:
- security issues were fixed by reducing secret exposure and failing closed,
- concurrency issues were fixed at the ownership/locking boundary,
- reliability issues were fixed by making state durable or asynchronous,
- infrastructure issues were fixed by aligning configuration at the actual runtime boundary,
- documentation/naming issues were fixed with canonical references instead of risky code churn.

That design direction is technically sound because it addresses root control points rather than layering superficial guards on top of the old behavior.
