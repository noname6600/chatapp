# Service Phase7: Realtime Extraction Preparation

## 1) Scope

In scope:
- chat-service
- presence-service
- friendship-service
- notification-service
- gateway-service

Out of scope respected:
- common
- frontend
- deployment/infrastructure

This phase is preparation only. No realtime-edge was introduced.

## 2) Temporary vs target realtime ownership

### Current temporary ownership (after this pass)
- Service-local websocket ingress remains active in chat/presence/friendship/notification.
- Gateway still forwards `/ws/**` paths to service-local websocket endpoints.
- Service-local realtime adapters continue local delivery (redis/websocket fanout).

### Target ownership (documented, not implemented)
- Single realtime-edge will own websocket ingress/auth/session routing.
- Services will continue emitting realtime publication intents through service-local publisher contracts.
- Realtime-edge will consume from stream/event publication and own fanout transport.

### Clarifications made in code
- Business/application services were documented as producers of outbound realtime intents, not long-term websocket owners.
- Websocket configs and gateway ws routing were explicitly marked temporary.
- Fake durable/ephemeral/mixed flow branches that did not implement distinct behavior were removed.

## 3) Files changed

### New explicit service-local publisher contracts
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/publisher/ChatRealtimeEventPublisher.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/realtime/publisher/PresenceRealtimeEventPublisher.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/realtime/publisher/FriendshipRealtimeEventPublisher.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/realtime/publisher/NotificationRealtimeEventPublisher.java`

### Port alignment to explicit publisher contracts
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/port/ChatRealtimePort.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/realtime/port/PresenceRealtimePort.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/realtime/port/FriendshipRealtimePort.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/realtime/port/NotificationRealtimePort.java`

### Fake flow-semantics removal
- Removed unused/non-distinct `RealtimeFlowId` overloads and flow-type branching in:
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketPublisher.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java`
- Removed corresponding flow-semantics method declarations from:
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/realtime/port/FriendshipRealtimePort.java`

### Temporary ownership documentation updates
- `chatappBE/chat-service/src/main/java/com/example/chat/config/WebSocketConfig.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/WebSocketConfig.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/FriendshipWebSocketConfig.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/configuration/WebSocketConfig.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/service/PresenceService.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationPushService.java`
- `chatappBE/gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java`
- `chatappBE/gateway-service/src/main/resources/application.yaml`

## 4) Validation

Compile command:
- `./gradlew.bat :chat-service:compileJava :presence-service:compileJava :friendship-service:compileJava :notification-service:compileJava :gateway-service:compileJava --continue --no-daemon`

Result:
- `BUILD SUCCESSFUL`

## 5) Remaining cutover work

1. Introduce realtime-edge websocket ingress and migrate gateway `/ws/*` routes from service-local targets to realtime-edge.
2. Move service-local websocket session registries/broadcasters behind edge-owned transport adapters.
3. Standardize event-stream publication contracts per service for edge consumption (chat/presence/friendship/notification).
4. Replace remaining service-local websocket-specific payload mapping in adapters with edge-owned mapping/fanout.
5. Add migration toggles/traffic strategy (shadow fanout or staged route cutover) and contract tests for edge parity.
