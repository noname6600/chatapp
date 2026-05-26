# Service Phase-0 Freeze Rules Pass

## 1. Scope
- Applied to backend service modules in `chatappBE/**` excluding `chatappBE/common/**`.
- No frontend or deployment/infrastructure files were touched.

## 2. Freeze rules applied/documented
- Added explicit phase-0 freeze documentation for business services.
- Replaced TODO comments that encouraged service-local WebSocket/Kafka duplication with migration notes toward a future realtime-edge pipeline.
- Preserved current behavior (no broad redesign, no route additions, no dependency graph changes).
- This pass did not add:
  - new browser WebSocket endpoints in business services
  - new service-to-service compile-time module dependencies
  - new business logic in WebSocket handlers or Kafka consumers
  - new routes under another service namespace

## 3. Files changed
- `chatappBE/SERVICE_PHASE0_FREEZE_RULES.md`
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketPublisher.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java`

## 4. Remaining risks
- Existing service-local realtime adapters/publishers still contain transport fanout behavior; freeze comments reduce drift risk but do not remove legacy shape.
- Without CI guardrails (architecture tests/lint rules), future PRs can still violate freeze rules unintentionally.
- Route-namespace and compile-time dependency constraints are documented but not yet enforced by automated checks in this pass.
