# Realtime Edge Service — Skeleton Design

**Status:** Initial skeleton for phased migration  
**Date:** May 12, 2026  
**Module:** `realtime-edge-service`

---

## 1. Purpose

The realtime edge service is a **connection and delivery boundary** that centralizes websocket ingress, session ownership, and client fanout for all realtime domains.

It is **NOT** a domain service and does **NOT** own business logic.

---

## 2. Responsibilities (In Scope)

### Connection Management
- ✅ WebSocket ingress (authenticated)
- ✅ Session registration/unregistration
- ✅ Authentication/authorization via JWT
- ✅ Session lifecycle (connect/disconnect)

### Session Ownership
- ✅ Centralized session registry (session → user → subscriptions)
- ✅ Multi-session support per user (edge tracks all user connections)
- ✅ Subscription tracking (user subscribed to which topics/rooms)
- ✅ Cross-session queries (find all sessions subscribed to room X)

### Inbound Command Routing
- ✅ Multiplex incoming commands by domain
- ✅ Parse envelope: {domain, type, payload}
- ✅ Route to appropriate domain handler (HTTP/gRPC client)
- ✅ NOT execute business logic (forward to domain services)

### Outbound Event Delivery
- ✅ Listen to domain service events (Redis pub/sub, Kafka topics)
- ✅ Deserialize events
- ✅ Determine subscriber scope (room, user, topic)
- ✅ Broadcast to subscribed sessions
- ✅ NOT own event creation (domain services create events)

---

## 3. Responsibilities (Out of Scope)

### Domain Business Logic
- ❌ Message creation/editing/deletion (chat-service)
- ❌ Friend request acceptance/rejection (friendship-service)
- ❌ Presence state management (presence-service)
- ❌ Notification creation (notification-service)

### Persistence
- ❌ Message storage
- ❌ User state
- ❌ Friendship state
- ❌ Presence history

### Domain Contracts
- ❌ SharedEventCatalog (use from common-events)
- ❌ Event versioning
- ❌ Event validation
- ❌ Domain-specific enums/types

### Infrastructure
- ❌ Kubernetes deployment
- ❌ Multi-DC failover
- ❌ Session state replication (yet)
- ❌ Service mesh integration

---

## 4. Module Structure

```
realtime-edge-service/
├── src/main/java/com/example/realtimeedge/
│   ├── RealtimeEdgeServiceApplication.java          [Main Spring Boot class]
│   │
│   ├── config/
│   │   ├── WebSocketConfig.java                     [Register handler at /ws]
│   │   └── ... (future: security, redis, kafka)
│   │
│   ├── websocket/
│   │   ├── handler/
│   │   │   └── CentralRealtimeWebSocketHandler.java [Multiplex entry point]
│   │   ├── session/
│   │   │   ├── RealtimeSession.java                 [Session model]
│   │   │   ├── IRealtimeSessionRegistry.java        [Registry interface]
│   │   │   └── InMemoryRealtimeSessionRegistry.java [In-memory impl]
│   │   └── subscription/
│   │       └── ... (future: subscription models)
│   │
│   ├── application/
│   │   ├── command/
│   │   │   └── ... (future: command models)
│   │   └── event/
│   │       └── ... (future: event models)
│   │
│   ├── routing/
│   │   └── command/
│   │       ├── ICommandRouter.java                  [Main dispatcher]
│   │       ├── CommandDispatcher.java               [Dispatcher impl]
│   │       ├── IChatCommandRouter.java              [Domain interfaces]
│   │       ├── IPresenceCommandRouter.java
│   │       ├── IFriendshipCommandRouter.java
│   │       └── INotificationCommandRouter.java
│   │
│   ├── delivery/
│   │   ├── event/
│   │   │   ├── RealtimeEventHandler.java            [Base handler]
│   │   │   ├── ChatEventHandler.java                [Chat events]
│   │   │   ├── PresenceEventHandler.java            [Presence events]
│   │   │   ├── FriendshipEventHandler.java          [Friendship events]
│   │   │   └── NotificationEventHandler.java        [Notification events]
│   │   ├── redis/
│   │   │   └── ... (future: Redis consumers)
│   │   └── kafka/
│   │       └── ... (future: Kafka consumers)
│   │
│   ├── security/
│   │   └── ... (future: JWT validation, auth interceptors)
│   │
│   └── support/
│       └── ... (future: utilities, monitoring, metrics)
│
├── src/main/resources/
│   └── application.yml                              [Configuration]
│
└── build.gradle                                     [Dependencies]
```

---

## 5. Session Ownership Model

### Current (Per-Service)

```
chat-service:       ChatSessionRegistry → manages only chat WebSocket sessions
presence-service:   PresenceSessionRegistry → manages only presence WebSocket sessions
notification-svc:   NotificationSessionRegistry → manages only notification WebSocket sessions
```

**Problem:** Each service has isolated session view; no awareness of multi-session users.

### After Edge (Centralized)

```
realtime-edge-service: CentralRealtimeSessionRegistry
    ├── session → user mapping
    ├── session → subscriptions (chat:room:123, presence:room:456, etc.)
    ├── subscription → sessions (for fanout)
    └── user → all sessions
```

**Benefit:** Edge knows full session state across all domains for accurate fanout.

### Example Data Flow

```
User connects via browser:
  1. Browser → /ws (WebSocket upgrade)
  2. CentralRealtimeWebSocketHandler.afterConnectionEstablished()
  3. Extract userId from JWT
  4. Register in CentralRealtimeSessionRegistry
  5. Session now tracked in edge

User subscribes to chat room 123:
  1. Browser sends: {domain:"chat", type:"JOIN_ROOM", payload:{roomId:123}}
  2. CommandDispatcher routes to ChatCommandRouter
  3. ChatCommandRouter calls HTTP POST chat-service/api/v1/chat/subscribe
  4. Response includes subscription confirmation
  5. Edge calls: sessionRegistry.addSubscription(sessionId, "chat:room:123")

Chat message arrives in room 123:
  1. chat-service publishes event to redis channel: realtime.chat.message.sent
  2. ChatEventHandler listens to channel
  3. Extracts roomId=123, builds message
  4. Calls: broadcastToSubscription("chat:room:123", message)
  5. Edge finds all sessions subscribed to "chat:room:123"
  6. Sends WebSocket message to each session
```

---

## 6. Inbound Command Routing Model

### Current (Service-Local)

```
Chat Client → chat-service WebSocket handler → chat domain service
Presence Client → presence-service WebSocket handler → presence domain service
```

**Problem:** Each client connects to different endpoint; no awareness of multi-domain sessions.

### After Edge (Multiplexed)

```
All Clients → realtime-edge-service /ws (CentralRealtimeWebSocketHandler)
    ├─ Parse: {domain:"chat", type:"SEND_MESSAGE", ...}
    ├─ Route via CommandDispatcher
    └─ → ChatCommandRouter (HTTP client to chat-service)
    └─ → PresenceCommandRouter (HTTP client to presence-service)
    └─ → FriendshipCommandRouter (HTTP client to friendship-service)
    └─ → NotificationCommandRouter (HTTP client to notification-service)
```

### Implementation Pattern

**Phase-based migration:**

1. **Phase B (Notification)** - First to migrate
   - Create RestNotificationCommandRouter with HTTP client
   - Edge receives notification subscribe commands
   - Forwards to notification-service HTTP endpoint

2. **Phase C (Presence)** - Second to migrate
   - Create RestPresenceCommandRouter with HTTP client
   - Edge receives presence status commands
   - Forwards to presence-service HTTP endpoint

3. **Phase D (Chat)** - Third to migrate
   - Create RestChatCommandRouter with HTTP client
   - Edge receives chat message commands
   - Forwards to chat-service HTTP endpoint

4. **Phase E (Friendship)** - Last to migrate
   - Create RestFriendshipCommandRouter with HTTP client
   - Edge receives friendship commands
   - Forwards to friendship-service HTTP endpoint

---

## 7. Outbound Event Fanout Model

### Current (Service-Local)

```
chat-service publishes to redis → local ChatRedisSubscriber → local broadcasters → connected clients
presence-service publishes to redis → local PresenceRedisSubscriber → local broadcasters → connected clients
```

**Problem:** Each service fans out only to its local WebSocket clients.

### After Edge (Centralized)

```
Domain Services (all) publish events
    ↓
redis pub/sub (realtime.{domain}.{entity}.{scope})
    ↓
EdgeService Consumers (ChatEventHandler, PresenceEventHandler, etc.)
    ↓
CentralRealtimeSessionRegistry queries subscribed sessions
    ↓
CentralRealtimeWebSocketHandler broadcasts to all subscribed clients
```

### Event Handler Pattern

Each domain has an event handler that:
1. Listens to domain service events (Redis/Kafka)
2. Extracts subscription scope (room, user, topic)
3. Queries session registry for subscribers
4. Sends WebSocket message to each subscriber

```java
ChatEventHandler.handleEvent(MESSAGE_SENT, payload):
    roomId = payload.getRoomId()
    subscription = "chat:room:" + roomId
    sessions = sessionRegistry.getSubscriberSessions(subscription)
    for each session:
        session.sendMessage(payload)
```

---

## 8. Current Architecture State

### Status: SKELETON WITH CLEAR BOUNDARIES

**Completed:**
- ✅ CentralRealtimeWebSocketHandler (multiplex entry point)
- ✅ RealtimeSession model (session data structure)
- ✅ IRealtimeSessionRegistry interface (registry contract)
- ✅ InMemoryRealtimeSessionRegistry implementation (dev/test ready)
- ✅ CommandDispatcher (route to domain routers)
- ✅ Domain router interfaces (IChatCommandRouter, etc.)
- ✅ Event handler base class (RealtimeEventHandler)
- ✅ Domain event handlers (ChatEventHandler, PresenceEventHandler, etc.)
- ✅ WebSocket configuration (register at /ws)
- ✅ Application properties (database, kafka, redis config)

**Not Yet Implemented (Future):**
- ❌ JWT validation in CentralRealtimeWebSocketHandler
- ❌ HTTP/gRPC clients for domain routers
- ❌ Redis/Kafka event consumers
- ❌ Distributed session store (Redis-backed registry)
- ❌ Multi-instance session replication
- ❌ Authentication interceptor
- ❌ Rate limiting per session
- ❌ Comprehensive monitoring/metrics

### What This Skeleton Enables

1. **Architecture Clarity:** Boundary between edge and domains now explicit in code
2. **Phase Planning:** Each phase has clear router to implement
3. **Testing:** Session registry can be tested in isolation
4. **Compilation:** Module compiles as valid Spring Boot service
5. **Future Extraction:** Existing domain services don't change yet

---

## 9. Migration Prerequisites (Before Cutover)

### Required Before Phase B (Notification)

- [ ] Implement JWT validation in CentralRealtimeWebSocketHandler
- [ ] Create RestNotificationCommandRouter with HTTP client
- [ ] Register NotificationCommandRouter in CommandDispatcher
- [ ] Implement Redis consumer for notification events
- [ ] Test notification flow: edge → HTTP → notification-svc → redis → edge → client
- [ ] Load test edge service with notification load

### Required Before Phase C (Presence)

- [ ] Create RestPresenceCommandRouter with HTTP client
- [ ] Implement Redis consumer for presence events
- [ ] Design presence room subscription model
- [ ] Test presence flow end-to-end
- [ ] Verify no presence state loss during migration

### Required Before Phase D (Chat)

- [ ] Create RestChatCommandRouter with HTTP client
- [ ] Implement Kafka consumer for durable chat events
- [ ] Test chat message durability through edge
- [ ] Verify no message loss

### Required Before Phase E (Friendship)

- [ ] Create RestFriendshipCommandRouter with HTTP client
- [ ] Low priority (least frequently used)

### Before Any Cutover

- [ ] Implement distributed session registry (Redis-backed)
- [ ] Implement cross-instance session awareness via pub/sub
- [ ] Load test: 10K+ concurrent sessions
- [ ] Chaos test: kill instance, verify session failover
- [ ] Rollback plan: revert traffic to service-local handlers

---

## 10. Constraints & Assumptions

### Constraints

- Edge does NOT own any business logic
- Edge does NOT store persistent data
- Edge does NOT validate business rules (domain services do)
- Edge only forwards commands to domain services
- Session registry is initially in-memory (not distributed)

### Assumptions

- Each domain service has a public API (HTTP or gRPC)
- Domain services publish events to Redis/Kafka
- Event format matches SharedEventCatalog
- Client can encode domain + command in JSON envelope
- Domain services are stateless or handle concurrent requests

### Design Decisions

1. **In-Memory Registry First:** Simpler to test; Redis-backed version comes later
2. **HTTP Clients:** Simpler than gRPC for initial phases; can optimize later
3. **Single /ws Endpoint:** All domains multiplex to single endpoint for simplicity
4. **No Business Logic:** Clear separation makes refactoring easier and safer
5. **Event Handler Base Class:** Reduces boilerplate for each domain

---

## 11. Compilation & Deployment Status

### Current Status

✅ **Module compiles** - All skeleton classes are syntactically correct
✅ **Spring Boot ready** - Application.java properly annotated
✅ **Dependencies declared** - build.gradle includes all necessary libraries
✅ **Configuration present** - application.yml has all required properties
✅ **Can start** - Service can boot (will warn about missing beans but starts)

### Test Start Command

```bash
cd chatappBE
./gradlew :realtime-edge-service:bootRun
```

Expected output:
```
INFO: Started RealtimeEdgeServiceApplication in 8.5 seconds
...
WebSocket at /ws endpoint ready
```

---

## 12. Future Roadmap (Out of Scope for This Skeleton)

### Multi-Instance Support
- Implement RedisSessionRegistry (distributed session store)
- Add pub/sub for cross-instance session awareness
- Session migration during instance restart

### Performance & Scale
- Connection pooling for HTTP clients
- gRPC for domain routers (lower latency)
- Session batching for bulk fanout
- Metrics/tracing integration

### Security Enhancements
- Rate limiting per session/user
- DDoS protection
- IP whitelisting
- Audit logging

### Operational
- Health checks for domain services
- Circuit breaker for failed domain calls
- Graceful degradation
- Session persistence across restarts

---

## 13. Files Created

| File | Purpose |
|------|---------|
| RealtimeEdgeServiceApplication.java | Spring Boot entry point |
| CentralRealtimeWebSocketHandler.java | Multiplex WebSocket handler |
| RealtimeSession.java | Session model |
| IRealtimeSessionRegistry.java | Registry interface |
| InMemoryRealtimeSessionRegistry.java | In-memory registry impl |
| ICommandRouter.java | Command dispatcher interface |
| CommandDispatcher.java | Route commands to domain routers |
| DomainCommandRouters.java | Domain router interfaces |
| RealtimeEventHandler.java | Event handler base |
| ChatEventHandler.java | Chat events → fanout |
| PresenceEventHandler.java | Presence events → fanout |
| FriendshipEventHandler.java | Friendship events → fanout |
| NotificationEventHandler.java | Notification events → fanout |
| WebSocketConfig.java | WebSocket configuration |
| application.yml | Service configuration |
| settings.gradle | Module registration |

---

## 14. Conclusion

The realtime-edge-service skeleton is **implementation-ready** for phased migration.

**What this delivers:**
- ✅ Clear architectural boundary (edge ≠ domain)
- ✅ Explicit session ownership model
- ✅ Multiplex command routing
- ✅ Event handler structure
- ✅ Compilation-ready code
- ✅ Foundation for 5 migration phases

**What's still needed:**
- HTTP/gRPC clients for each domain (per phase)
- JWT validation (security)
- Distributed session registry (scale)
- Event consumers (delivery)

**Timeline to production:**
- Skeleton phase: ✅ Complete (this deliverable)
- Phase B (Notification): 2-3 weeks
- Phase C (Presence): 2-3 weeks
- Phase D (Chat): 3-4 weeks
- Phase E (Friendship): 1-2 weeks
- Scale & hardening: 2-3 weeks

Total estimated: **12-16 weeks** to full production migration

---

**Prepared By:** Realtime Edge Service Architecture  
**Last Updated:** May 12, 2026  
**Status:** Skeleton ready for Phase B (Notification) to begin  
**Next Steps:** Implement RestNotificationCommandRouter and Redis event consumer
