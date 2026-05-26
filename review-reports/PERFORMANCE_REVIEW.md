# Performance Review

## Findings (ordered by severity)

### 1) HIGH - Synchronous HTTP in WebSocket flow introduces head-of-line blocking
- Severity: HIGH
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java`; `adapter/out/presence/PresenceDomainClient.java`
- Root cause: Frame handling directly invokes blocking HTTP calls.
- Impact: Elevated p95/p99 latency for active websocket clients.
- Reproduction risk: High under downstream latency spikes.
- Scalability risk: High.
- Recommended fix direction: Offload command side effects from ws thread path with bounded async queues.
- Shared/common changes required: No

### 2) HIGH - Per-session synchronized send can throttle high-volume channels
- Severity: HIGH
- Exact location: realtime-edge delivery services and `EdgeDeliveryHandoffListener` (`synchronized (webSocketSession)`)
- Root cause: Single lock serializes all sends per session.
- Impact: Throughput collapse for bursty channels and slow clients.
- Reproduction risk: Medium.
- Scalability risk: High.
- Recommended fix direction: Non-blocking write orchestration and backpressure strategy.
- Shared/common changes required: No

### 3) MEDIUM - Dedupe guards perform O(n) cleanup on each event check
- Severity: MEDIUM
- Exact location: `friendship-service/.../FriendshipEventDedupeGuard.java`; `realtime-edge-service/.../FriendshipEventDedupeGuard.java`
- Root cause: `seen.entrySet().removeIf(...)` executed on each dedupe call.
- Impact: CPU overhead grows with dedupe key cardinality.
- Reproduction risk: Medium.
- Scalability risk: Medium to High.
- Recommended fix direction: Time-bucketed or scheduled cleanup with bounded structures.
- Shared/common changes required: No

### 4) MEDIUM - Gateway timeout profile is globally aggressive for mixed traffic
- Severity: MEDIUM
- Exact location: `gateway-service/src/main/resources/application.yaml` (`connect-timeout: 5000`, `response-timeout: 5s`)
- Root cause: One timeout profile for heterogeneous endpoints.
- Impact: Early failures for temporarily slow but healthy dependencies.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Per-route timeout classes aligned with SLO tiers.
- Shared/common changes required: No

### 5) MEDIUM - Room access authorization path can generate external load spikes
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/subscription/ChannelSubscriptionManager.java`
- Root cause: Cache miss invokes chat authorization call; high churn channels can amplify request volume.
- Impact: Extra load on chat-service under reconnect storms.
- Reproduction risk: Medium.
- Scalability risk: High.
- Recommended fix direction: Improve cache invalidation/selective prefetch and monitor auth miss rate.
- Shared/common changes required: No

### 6) LOW - Mixed servlet + reactive stacks in edge increase runtime complexity
- Severity: LOW
- Exact location: `realtime-edge-service/build.gradle` (`spring-boot-starter-web`, `websocket`, and `webflux`)
- Root cause: Multi-stack runtime in one service.
- Impact: Harder tuning and diagnostics for thread/IO behavior.
- Reproduction risk: Low.
- Scalability risk: Medium.
- Recommended fix direction: Keep one dominant runtime model and explicitly isolate exceptions.
- Shared/common changes required: No
