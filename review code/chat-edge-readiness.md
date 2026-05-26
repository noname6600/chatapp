# Chat Edge Readiness — Post-Boundary Refactor Assessment

**Evaluation Date:** May 12, 2026  
**Assessment Scope:** Chat-service realtime architecture after boundary refactoring  
**Previous State:** [chat-realtime-boundary-refactor.md](chat-realtime-boundary-refactor.md)

---

## 1. Executive Summary

**Readiness for edge-service extraction: IMPROVED BUT NOT YET READY**

The boundary refactoring has significantly clarified chat realtime architecture by:
- Creating explicit lifecycle boundary via ChatConnectionLifecycleAdapter
- Creating explicit command routing via ChatCommandDispatcher
- Making separation of concerns visible in code

However, chat-service is NOT yet ready for extraction to edge because:
- Flow policy is incomplete (Kafka durable-first not implemented)
- Pin/unpin event contract conflicts need resolution
- Runtime verification of full flows not yet done post-refactor
- Session registry and websocket handler are still tightly coupled to domain

---

## 2. Readiness Scorecard

### Before Refactor

| Dimension | Status | Evidence |
|---|---|---|
| **Boundary Clarity** | Unclear | ChatWebSocketHandler mixed protocol + domain concerns |
| **Command Routing** | Implicit | Direct domain calls from websocket handler |
| **Lifecycle Handling** | Implicit | No explicit lifecycle adapter |
| **Adapter Semantics** | N/A | Only one delivery path (direct fanout) |
| **Extraction Feasibility** | Low | Commands hardcoded in handler; session tied to domain |
| **Readiness** | ❌ Not Ready | Tight coupling blocks extraction |

### After Refactor

| Dimension | Status | Evidence |
|---|---|---|
| **Boundary Clarity** | Explicit | ChatConnectionLifecycleAdapter + ChatCommandDispatcher created |
| **Command Routing** | Routed | Commands delegated to ChatCommandDispatcher |
| **Lifecycle Handling** | Explicit | ChatConnectionLifecycleAdapter signals events |
| **Adapter Semantics** | Clear | Adapters documented with future migration paths |
| **Extraction Feasibility** | Medium | Adapters can be replaced with HTTP/gRPC; session registry still local |
| **Readiness** | ⏳ Ready for Phase 2 | Foundations laid; more work needed |

---

## 3. What's Now Clear and Explicit

### 3.1 Lifecycle Boundary

**Before:**
```
ChatWebSocketHandler
  → (just registered session, no structure)
  → afterConnectionEstablished() had no separate boundary
```

**After:**
```
ChatWebSocketHandler
  → ChatConnectionLifecycleAdapter
  → Explicit lifecycle signals (onConnectionEstablished, onRoomJoined, onRoomLeft)
  → Can be replaced during edge extraction
```

**Benefit:** Future edge-service knows exactly what signals to send back to chat-service

### 3.2 Command Routing Boundary

**Before:**
```
ChatWebSocketHandler.dispatchCommand()
  → direct calls to messageCommandService
  → direct calls to reactionCommandService
  (No abstraction)
```

**After:**
```
ChatWebSocketHandler.dispatchCommand()
  → ChatCommandDispatcher.dispatch(session, command)
  → routes to messageCommandService via dispatcher
  (Clear delegation point for future HTTP/gRPC)
```

**Benefit:** Edge-service can route commands via HTTP/gRPC to this dispatcher endpoint

### 3.3 Session Management Remains Local

**Current:**
```
ChatSessionRegistry
  → Still manages session→user and session→room mappings
  → Still provides query methods for broadcasters
  → Still tied to local websocket handler
```

**Status:** This MUST move to edge-service during extraction, not chat-service

---

## 4. Extraction Feasibility Analysis

### Can We Extract Websocket Now?

**No.** Several blockers:

1. **ChatSessionRegistry is Service-Local**
   - Manages session→user→room mappings
   - No server-side state tracking (stateless-like)
   - Must move to edge, not stay in chat-service

2. **Broadcasters Are Service-Local**
   - WebSocketRoomBroadcaster/WebSocketUserBroadcaster
   - Must move to edge for centralized session ownership

3. **ChatWebSocketHandler Still Owns Protocol**
   - Must move to edge
   - But chat-service needs to remain as command receiver

### Can We Replace Adapters?

**Yes, partially:**

1. **ChatConnectionLifecycleAdapter** ✅ Easily Replaceable
   - Currently calls sessionRegistry directly
   - Can be replaced with HTTP PUT `/lifecycle/connection` endpoint

2. **ChatCommandDispatcher** ✅ Easily Replaceable
   - Currently calls domain services
   - Can be replaced with HTTP POST `/commands/{type}` endpoint

3. **ChatWebSocketHandler** ⏳ Partially Replaceable
   - Protocol handling must move to edge
   - Session registry queries must move to edge
   - Command dispatch can call HTTP to chat-service

---

## 5. Readiness by Component

### Session Registry & Broadcasters

**Status:** ❌ NOT READY FOR EDGE EXTRACTION

**Reason:**
- Currently service-local, tightly tied to websocket handler
- Must be centralized in edge-service for multi-service session tracking
- Requires significant refactoring to move

**What's Needed:**
1. Design centralized session registry in edge-service
2. Implement session query/management in edge-service
3. Update broadcasters to use edge-service session registry
4. Test multi-service fanout from edge

**Blocker Level:** CRITICAL - Must solve before websocket extraction

---

### ChatCommandDispatcher

**Status:** ✅ READY FOR HTTP/gRPC REPLACEMENT

**Reason:**
- Now explicit and routed
- Domain services remain in chat-service
- Can be replaced with HTTP POST to chat-service

**What's Needed:**
1. REST endpoint `/api/v1/chat/commands/{type}` in chat-service
2. Map HTTP request to ChatCommandDispatcher methods
3. Test full round-trip: edge → HTTP → chat-service

**Effort:** 2-3 days to implement and test

---

### ChatConnectionLifecycleAdapter

**Status:** ✅ READY FOR HTTP/gRPC REPLACEMENT

**Reason:**
- Now explicit and isolated
- Only signals lifecycle events
- Can be replaced with HTTP calls

**What's Needed:**
1. Lifecycle endpoint `/api/v1/chat/lifecycle/events` in chat-service
2. Accept connection/disconnection/room events as JSON
3. Route to lifecycle adapter

**Effort:** 1-2 days to implement

---

### ChatRealtimeAdapter (Delivery Path)

**Status:** ⏳ PARTIALLY READY

**Issue:** Flow policy incomplete (TODO comments for Kafka)

**What's Needed:**
1. Implement Kafka publish for MESSAGE_PINNED/MESSAGE_UNPINNED
2. Test durable-first flow semantics
3. Verify no message loss under high concurrency

**Effort:** 3-5 days including tests

**Blocker Level:** MEDIUM - Should complete before extraction, but not blocking boundary clarity

---

### Chat Domain Services

**Status:** ✅ STAYS IN CHAT-SERVICE (No changes needed)

**Reason:** Business logic for messages/reactions is chat-specific and should remain

---

## 6. Pre-Extraction Checklist

### Completed ✅

- ✅ Boundary adapters created (lifecycle, dispatcher)
- ✅ ChatWebSocketHandler refactored to use adapters
- ✅ Separation of concerns is explicit
- ✅ Future migration paths documented

### Required Before Extraction ❌

- ❌ Kafka integration in ChatRealtimeAdapter (flow policy completion)
- ❌ Pin/unpin contract conflict resolution (MessagePinPayload vs RoomMessagePinEventPayload)
- ❌ HTTP endpoints for lifecycle/command routing
- ❌ Runtime verification of full chat flow post-refactor
- ❌ Edge-service session registry design
- ❌ Centralized broadcaster implementation for edge

### Nice-to-Have Before Extraction

- ⏳ Unit tests for ChatConnectionLifecycleAdapter
- ⏳ Unit tests for ChatCommandDispatcher
- ⏳ Integration tests for command round-trip (handler → dispatcher → domain)

---

## 7. Session Extraction Complexity

### Why Session Registry Must Move to Edge

**Current (Per-Service):**
```
chat-service: ChatSessionRegistry → manages chat sessions
presence-service: PresenceSessionRegistry → manages presence sessions
notification-service: NotificationSessionRegistry → manages notification sessions
```

**Problem:** Each service has its own session view; no centralized ownership

**Future (Edge-Centered):**
```
realtime-edge-service: CentralRealtimeSessionRegistry
  ├─ Session → User
  ├─ Session → Subscriptions (chat rooms, presence rooms, notifications)
  ├─ Room → Sessions (for fanout)
  └─ Used by centralized broadcasters
```

**Complexity:** HIGH
- Requires designing new data structures
- Must support multi-service fanout
- Lifecycle signals from chat, presence, friendship must sync with edge registry

**Effort:** 2-3 weeks design + implementation

---

## 8. Extraction Sequence (Recommended)

### Phase 1: Kafka Integration (1 sprint)
1. Complete ChatRealtimeAdapter flow policy
2. Implement Kafka publish for durable-first flows
3. Runtime verification of chat flows

### Phase 2: Contract Stabilization (2-3 days)
1. Resolve pin/unpin payload conflict (MessagePinPayload vs RoomMessagePinEventPayload)
2. Verify no side effects

### Phase 3: Edge-Service Design (1-2 weeks)
1. Design centralized session registry
2. Design broadcaster architecture
3. Design command routing from edge to services

### Phase 4: HTTP/gRPC Endpoints (1 week)
1. Add lifecycle endpoint to chat-service
2. Add command endpoint to chat-service
3. Test endpoint security and reliability

### Phase 5: Websocket Migration (2-3 weeks)
1. Implement edge-service websocket handler
2. Migrate chat websocket connections to edge (session by session)
3. Test full end-to-end flow
4. Verify no message loss

### Phase 6: Cleanup (1 week)
1. Remove service-local websocket handlers
2. Remove service-local session registries
3. Verify no functionality regression

---

## 9. What's Still Unclear

### Not Proven End-to-End

- ⏳ Full chat message flow after refactoring (handler → dispatcher → domain → publisher → fanout)
- ⏳ Room join/leave lifecycle after refactoring
- ⏳ Kafka durability for pin/unpin after implementation

**Why:** No runtime tests executed post-refactoring. Behavior should be identical (no logic changes), but should be verified.

---

## 10. Comparison: Presence vs Chat Readiness

| Aspect | Presence | Chat |
|---|---|---|
| **Connection Lifecycle Adapter** | ✅ Clear | ✅ Clear |
| **Session Registry** | ✅ Working | ✅ Working |
| **Command Routing** | N/A (no commands) | ✅ Explicit now |
| **Domain Coupling** | 🟠 Still present (online/offline) | ✅ Well-separated |
| **Flow Policy** | ✅ Complete | ⏳ Incomplete |
| **Runtime Verified** | ⏳ Not yet | ⏳ Not yet |
| **Extraction Readiness** | 5/10 (foundations laid) | 5/10 (boundaries clearer) |

---

## 11. Readiness Decision

### Can Chat-Service Websocket Be Extracted Now?

**No.** Prerequisites not yet met:
1. Flow policy incomplete
2. Session registry design not started
3. HTTP endpoints not implemented
4. Runtime verification not done post-refactoring

### Should Chat-Service Proceed to Phase 2?

**Yes.** Next steps are well-defined:
1. Complete Kafka flow policy
2. Resolve contract conflicts
3. Run runtime verification
4. Design edge-service architecture

### Overall Extraction Readiness

- **Current:** 5/10 (improved from 3/10)
- **After Phase 1 (Kafka):** 6/10
- **After Phase 2 (Contracts):** 7/10
- **After Phase 3-4 (Design + Endpoints):** 8/10
- **After Phase 5 (Migration):** Ready for extraction

---

## 12. Key Improvements Made

✅ **ChatConnectionLifecycleAdapter** - Explicit boundary for lifecycle signals  
✅ **ChatCommandDispatcher** - Explicit boundary for command routing  
✅ **Documentation** - Future migration paths clear  
✅ **Separation** - Websocket protocol vs domain logic now distinct  
✅ **Replaceability** - Adapters can be swapped for HTTP/gRPC  

---

## 13. Remaining Work to Edge-Ready

**Estimated 4-6 weeks after this refactoring:**

1. Kafka flow policy completion (3-5 days)
2. Contract conflict resolution (2-3 days)
3. Runtime verification (1 week)
4. Edge-service design (1-2 weeks)
5. HTTP endpoint implementation (1 week)
6. Session registry design and implementation (2-3 weeks)
7. Websocket migration testing (1 week)

---

## 14. Conclusion

Chat-service realtime architecture is **CLEARER AND MORE STRUCTURED** after the boundary refactoring, but **NOT YET READY FOR EXTRACTION**.

The refactoring successfully:
- ✅ Created explicit boundaries (lifecycle, dispatcher)
- ✅ Made future replacements clear
- ✅ Documented migration paths
- ✅ Improved code clarity without changing behavior

The next phase must:
- ❌ Complete flow policy (Kafka integration)
- ❌ Resolve contract conflicts
- ❌ Design centralized session management
- ❌ Implement HTTP/gRPC endpoints

**Recommendation:** Proceed to Phase 1 (Kafka integration) as next work item.

---

**Prepared By:** Chat Realtime Readiness Assessment  
**Last Updated:** May 12, 2026  
**Extraction Readiness:** 5/10 (improved from 3/10, need 8/10+ for extraction)  
**Next Assessment:** After Kafka integration completion
