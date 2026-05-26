# WebSocket Review

Primary implementation reviewed in realtime-edge-service.

## Findings (ordered by severity)

### 1) CRITICAL - Public WebSocket route contract conflicts with edge handler path
- Severity: CRITICAL
- Exact location: `gateway-service/src/main/resources/application.yaml` (`/ws/**` route/rewrite) vs `realtime-edge-service/src/main/java/com/chatweb/realtime/config/WebSocketConfig.java` (`/realtime`)
- Root cause: Gateway rewrite does not normalize incoming `/ws/*` paths to edge handler endpoint.
- Impact: Upgrade failures or endpoint mismatch for realtime clients.
- Reproduction risk: High.
- Scalability risk: High.
- Recommended fix direction: Define and enforce one external WebSocket URI contract end-to-end.
- Shared/common changes required: No

### 2) HIGH - Blocking HTTP calls executed on WebSocket message path
- Severity: HIGH
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java` (`handlePresenceMessage`, `handleChatMessage`) calling `PresenceDomainClient`/chat router; `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/out/presence/PresenceDomainClient.java` uses synchronous `RestClient`
- Root cause: Synchronous HTTP side effects are executed inline in WebSocket frame handling.
- Impact: Increased frame latency, head-of-line blocking, degraded tail latency under dependency slowness.
- Reproduction risk: High under transient downstream latency.
- Scalability risk: High.
- Recommended fix direction: Isolate network calls from frame loop (queue/async command pipeline with timeout and backpressure).
- Shared/common changes required: No

### 3) HIGH - Serialized per-session send lock can amplify latency
- Severity: HIGH
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/ChatRealtimeDeliveryService.java`, `PresenceRealtimeDeliveryService.java`, `NotificationRealtimeDeliveryService.java`, `FriendshipRealtimeDeliveryService.java`, `dispatch/EdgeDeliveryHandoffListener.java` (`synchronized (webSocketSession)`)
- Root cause: Synchronous send under monitor lock on session object.
- Impact: Slow socket write blocks other outgoing sends for same session.
- Reproduction risk: Medium.
- Scalability risk: High with noisy channels and slow clients.
- Recommended fix direction: Use per-session outbound queue and non-blocking flush strategy.
- Shared/common changes required: No

### 4) MEDIUM - Access token retained in WebSocket session attributes
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java` (`attributes.put("accessToken", accessToken)`), consumed in handler
- Root cause: Raw bearer token is stored in long-lived in-memory attributes.
- Impact: Increased secret exposure surface in heap dumps/logging incidents.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Keep minimal claims/session principal data; avoid retaining full token.
- Shared/common changes required: No

### 5) MEDIUM - No mid-session JWT revalidation policy
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java` and `RealtimeWebSocketHandler.java`
- Root cause: JWT validated at handshake only; no explicit expiry enforcement for existing sessions.
- Impact: Session can outlive token expiry unless disconnected.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Introduce session validity checks tied to token expiry/refresh policy.
- Shared/common changes required: No

### 6) MEDIUM - Session metadata is distributed, socket objects are node-local
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/connection/RedisRealtimeSessionRegistry.java` and `RealtimeWebSocketSessionStore.java`
- Root cause: Session index is shared in Redis while physical `WebSocketSession` references remain in local memory by design.
- Impact: Any routing/ownership drift causes missed deliveries.
- Reproduction risk: Medium.
- Scalability risk: High in multi-instance deployments.
- Recommended fix direction: Keep strict ownership checks and observability for ownership drift/handoff misses.
- Shared/common changes required: No

### 7) LOW - Protocol-level ping/pong is not explicitly handled
- Severity: LOW
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java` (application `PING` message only)
- Root cause: App-level ping exists but no explicit protocol-level pong policy in handler.
- Impact: Harder to standardize idle/liveness behavior across clients/proxies.
- Reproduction risk: Low.
- Scalability risk: Medium.
- Recommended fix direction: Define explicit transport heartbeat policy and metrics.
- Shared/common changes required: No
