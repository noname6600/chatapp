# Realtime Edge Service — Migration Phase Plan

**Status:** Phased migration strategy for realtime-edge-service  
**Created:** May 12, 2026  
**Timeline:** 12-16 weeks estimated (5 phases)

---

## Overview

This document outlines the planned migration phases to move from service-local websocket handling to centralized realtime-edge-service.

**Key Principle:** Each phase is independent and can be rolled back individually. No phase depends on previous phases being complete.

**Risk Mitigation:** Each phase uses HTTP routing with easy traffic switch-back if needed.

---

## Phase A: Foundation (COMPLETE ✅)

**Duration:** 1 week  
**Status:** Complete - this skeleton delivery  
**Owner:** Architecture team

### Deliverables

- ✅ realtime-edge-service module scaffold
- ✅ CentralRealtimeWebSocketHandler
- ✅ CentralRealtimeSessionRegistry
- ✅ CommandDispatcher structure
- ✅ Event handler base classes
- ✅ Design documentation

### Validation

- [ ] Module compiles without errors
- [ ] Spring Boot application starts
- [ ] WebSocket endpoint available at /ws
- [ ] Session registry stores/retrieves sessions correctly

### Success Criteria

✅ Edge service is **deployable** but not yet **active** (no traffic routed)

### Rollback Plan

No active traffic yet, nothing to rollback.

---

## Phase B: Notification Migration

**Duration:** 2-3 weeks  
**Owner:** Notification service team + edge platform team

### Scope

Migrate notification realtime delivery to edge-service.

**Current Flow (Per-Service):**
```
Client → notification-service /ws → notification domain → redis → client
```

**Target Flow (Edge):**
```
Client → realtime-edge-service /ws → RestNotificationCommandRouter → 
  notification-service /api/v1/notifications/commands → redis → 
  NotificationEventHandler → client
```

### Implementation Tasks

1. **RestNotificationCommandRouter** (5 days)
   - Implement INotificationCommandRouter interface
   - Create HTTP client to notification-service
   - Map commands: SUBSCRIBE, UNSUBSCRIBE, SET_PREFERENCES
   - Error handling and retry logic
   - Tests: 80% coverage

2. **Notification Event Consumer** (5 days)
   - Implement Redis listener for `realtime.notification.*` channels
   - Parse NotificationPayload from SharedEventCatalog
   - Route to NotificationEventHandler
   - Handle backpressure and errors
   - Tests: 80% coverage

3. **HTTP Endpoint in Notification Service** (3 days)
   - New endpoint: `POST /api/v1/notifications/commands`
   - Deserialize command from edge
   - Execute domain logic
   - Return success/error
   - No breaking changes to existing endpoints

4. **Load Testing** (5 days)
   - Simulate 10K notification subscriptions
   - Test fanout latency
   - Test concurrent command handling
   - Verify no message loss under load

5. **Canary Deployment** (5 days)
   - Deploy edge-service to 1 node in staging
   - Route 10% notification traffic to edge
   - Monitor for 24 hours
   - Verify latency, error rates
   - Rollback if issues found

6. **Production Rollout** (3 days)
   - Deploy to all edge instances
   - Route 50% traffic to edge
   - Monitor for 24 hours
   - Route 100% traffic to edge
   - Keep service-local handler as fallback

### Validation Checklist

- [ ] RestNotificationCommandRouter handles all command types
- [ ] NotificationEventHandler correctly broadcasts events
- [ ] No notification delivery latency regression (should improve)
- [ ] Load test shows <100ms latency at 10K subscribers
- [ ] Canary deployment shows <0.1% error rate
- [ ] Production sees 0 dropped notifications in 24h

### Rollback Plan

If issues in production:

1. **Route traffic back to service-local handler**
   - Gateway routes `/ws/*` back to notification-service
   - Takes ~30 seconds
   - All connected sessions immediately disconnected (brief interruption expected)
   - Clients reconnect to service-local handler

2. **Investigation**
   - Check logs in edge-service
   - Check notification-service HTTP endpoint responses
   - Verify Redis channels working

3. **Deploy fix and retry**
   - Or keep service-local until fix is ready

### Success Criteria

✅ All notification traffic routed through edge-service  
✅ <10% increase in delivery latency (or improvement)  
✅ 0 lost notifications during migration  
✅ <0.01% error rate sustained for 7 days

---

## Phase C: Presence Migration

**Duration:** 2-3 weeks  
**Owner:** Presence service team + edge platform team  
**Prerequisite:** Phase B complete and stable for 1 week

### Scope

Migrate presence realtime delivery to edge-service.

**Complexity:** Medium
- Presence updates frequently (online/offline/idle changes)
- State changes tied to connection lifecycle (new in edge)
- Room membership tracking different from chat/notification

**Challenge:** Currently, presence-service manages connection lifecycle directly (calls presenceService.online() on connect). Must adapt to edge model where edge owns connections.

### Implementation Tasks

1. **RestPresenceCommandRouter** (5 days)
   - Implement IPresenceCommandRouter interface
   - Create HTTP client to presence-service
   - Map commands: UPDATE_STATUS, JOIN_ROOM, LEAVE_ROOM
   - Existing PresenceConnectionLifecycleAdapter helps here!
   - Tests: 80% coverage

2. **Presence Event Consumer** (5 days)
   - Implement Redis listener for `realtime.presence.*` channels
   - Parse presence events from domain
   - Route to PresenceEventHandler
   - Handle room membership updates
   - Tests: 80% coverage

3. **Adapt Presence Lifecycle** (5 days)
   - Challenge: Presence currently receives `online()` call on websocket connect
   - Solution: When session first connects to presence domain, edge calls:
     - HTTP POST `/api/v1/presence/connect?userId={userId}`
   - When session disconnects, edge calls:
     - HTTP POST `/api/v1/presence/disconnect?userId={userId}`
   - Presence service remains stateless (just processes signal)
   - Edge tracks actual connection state

4. **HTTP Endpoint in Presence Service** (3 days)
   - New endpoints:
     - `POST /api/v1/presence/connect` (signal connection start)
     - `POST /api/v1/presence/disconnect` (signal connection end)
     - `POST /api/v1/presence/commands` (handle commands)
   - No breaking changes

5. **Load Testing** (5 days)
   - Simulate 10K presence subscriptions
   - Test frequent status updates
   - Verify room membership broadcasts correctly
   - Test presence room joins/leaves

6. **Canary & Production** (same as Phase B)

### Validation Checklist

- [ ] Online/offline signals properly routed through edge
- [ ] Room membership tracked correctly
- [ ] Presence status updates broadcast to subscribers
- [ ] No presence state loss during migration
- [ ] Latency acceptable (same as before or better)
- [ ] Load test: 100+ status updates per second

### Rollback Plan

Same as Phase B (traffic back to service-local handler)

### Success Criteria

✅ All presence traffic routed through edge-service  
✅ Online/offline transitions work correctly  
✅ No missed presence broadcasts  
✅ <0.01% error rate sustained for 7 days

---

## Phase D: Chat Migration

**Duration:** 3-4 weeks  
**Owner:** Chat service team + edge platform team  
**Prerequisite:** Phase B & C complete and stable for 2 weeks

### Scope

Migrate chat realtime delivery to edge-service.

**Complexity:** High
- Kafka durable event support (not just Redis ephemeral)
- Highest message volume
- Most complex command types (SEND, EDIT, DELETE, REACT, PIN)
- ChatConnectionLifecycleAdapter + ChatCommandDispatcher already in place!

### Implementation Tasks

1. **RestChatCommandRouter** (7 days)
   - Implement IChatCommandRouter interface
   - Create HTTP client to chat-service
   - Map commands: SEND_MESSAGE, EDIT_MESSAGE, DELETE_MESSAGE, REACT, PIN, UNPIN
   - Leverage existing ChatCommandDispatcher as reference
   - Response should include message metadata for client
   - Tests: 90% coverage (high risk domain)

2. **Chat Event Consumer - Redis** (5 days)
   - Implement Redis listener for `realtime.chat.*` channels (ephemeral)
   - Parse chat events from domain
   - Route to ChatEventHandler
   - Fast path for room broadcasts

3. **Chat Event Consumer - Kafka** (7 days)
   - Implement Kafka consumer for durable chat events
   - Topics: chat.message.sent, chat.message.edited, chat.message.deleted
   - Ensure no message loss under broker failure
   - Handle consumer lag gracefully
   - Tests: 90% coverage

4. **HTTP Endpoint in Chat Service** (5 days)
   - New endpoint: `POST /api/v1/chat/commands/{type}`
   - Deserialize command from edge
   - Execute via ChatCommandDispatcher (no changes needed there!)
   - Return serialized message/event
   - No breaking changes

5. **Chat-Specific Flow Policy** (5 days)
   - Implement RealtimeFlowId support in edge
   - DURABLE_FIRST flows: publish to Kafka first, then Redis
   - EPHEMERAL_ONLY flows: direct fanout only
   - MIXED_WITH_CONVERGENCE flows: both with dedup

6. **Load Testing** (10 days)
   - Simulate 100K messages per second
   - Simulate 10K concurrent chat rooms
   - Test message ordering per room
   - Test message durability (Kafka)
   - Test pin/unpin contract (resolve MessagePinPayload conflict first!)
   - Verify <200ms end-to-end latency

7. **Canary & Production** (5 days, same pattern as Phase B)

### Key Dependency

**MUST RESOLVE BEFORE STARTING:**
- Pin/unpin contract conflict (MessagePinPayload vs RoomMessagePinEventPayload)
- See: current-realtime-ownership-review.md
- Estimated 2-3 days to fix

### Validation Checklist

- [ ] All chat commands route through edge correctly
- [ ] Message ordering preserved per room
- [ ] Message durability verified (no loss under Kafka failure)
- [ ] React/pin/unpin work correctly
- [ ] Load test: 100K msg/sec with <200ms latency
- [ ] Canary shows <0.001% error rate
- [ ] Chat room broadcasts work for 10K concurrent users

### Rollback Plan

Same as Phase B, but note:
- May lose in-flight messages from Kafka (acceptable, re-send after reconnect)
- Messages already in Redis will still be delivered

### Success Criteria

✅ All chat traffic routed through edge-service  
✅ 100K+ messages per second without degradation  
✅ Durable message handling (no loss)  
✅ <200ms end-to-end latency maintained  
✅ 0 message ordering violations  
✅ <0.001% error rate sustained for 7 days

---

## Phase E: Friendship Migration

**Duration:** 1-2 weeks  
**Owner:** Friendship service team + edge platform team  
**Prerequisite:** Phase D complete and stable for 2 weeks

### Scope

Migrate friendship realtime delivery to edge-service.

**Complexity:** Low
- Lowest message volume
- Simple command types (SEND_REQUEST, ACCEPT, DECLINE, UNFRIEND)
- FriendshipEventProducer already fixed in Phase 1
- Can be done in parallel with Phase D if needed

### Implementation Tasks

1. **RestFriendshipCommandRouter** (3 days)
   - Implement IFriendshipCommandRouter interface
   - Create HTTP client to friendship-service
   - Map commands: SEND_REQUEST, ACCEPT_REQUEST, DECLINE_REQUEST, UNFRIEND, BLOCK, UNBLOCK
   - Tests: 80% coverage

2. **Friendship Event Consumer** (3 days)
   - Implement Redis listener for `realtime.friendship.*` channels
   - Parse friendship events (already properly formatted post-Phase 1)
   - Route to FriendshipEventHandler
   - Tests: 80% coverage

3. **HTTP Endpoint in Friendship Service** (2 days)
   - New endpoint: `POST /api/v1/friendship/commands`
   - Execute via domain service
   - No breaking changes

4. **Load Testing** (3 days)
   - Simulate 5K concurrent friendship operations
   - Verify friend request broadcasts
   - Test unfriend/block operations

5. **Canary & Production** (3 days)

### Validation Checklist

- [ ] Friend requests properly broadcast
- [ ] Accept/decline transitions work
- [ ] Unfriend/block operations synchronized
- [ ] Error rate <0.1%

### Rollback Plan

Same as Phase B

### Success Criteria

✅ All friendship traffic routed through edge  
✅ Friend operations complete successfully  
✅ <0.1% error rate

---

## Post-Phases: Scale & Harden

**Duration:** 2-3 weeks  
**Owner:** Platform team

### Tasks (After all domains migrated)

1. **Distributed Session Registry** (1 week)
   - Replace InMemoryRealtimeSessionRegistry with RedisSessionRegistry
   - Session state persisted in Redis with TTL
   - Multi-instance deployment support
   - Cross-instance session awareness via pub/sub

2. **Multi-Instance Testing** (1 week)
   - Deploy edge-service on 3+ instances
   - Test session migration between instances
   - Test load balancing
   - Verify no session loss

3. **Chaos Testing** (1 week)
   - Kill edge instance while 10K connections active
   - Verify connections fail over to other instances
   - Kill domain service, verify edge graceful degradation
   - Network partition scenarios

4. **Performance Tuning** (1 week)
   - Profile message latency end-to-end
   - Optimize hot paths
   - Connection pooling for HTTP clients
   - Consider gRPC for highest-volume domains

---

## Risk Mitigation

### Risk 1: Service-Local Handler Removal Breaks Fallback

**Mitigation:** Keep service-local websocket handlers running throughout migration. They only turn off after Phase E + hardening.

### Risk 2: Network Latency Between Edge and Domain Services

**Mitigation:** 
- Edge and domain services should be co-located in same availability zone
- HTTP keeps connections alive (connection pooling)
- gRPC fallback for lowest latency in later phases

### Risk 3: Session Loss During Instance Failure

**Mitigation:** After Phase E, implement Redis-backed session registry with per-instance awareness.

### Risk 4: Message Loss During Kafka Consumer Deployment

**Mitigation:** 
- Use consumer groups with offset management
- Never auto-reset offsets
- Manual offset reset only after verification

### Risk 5: Cascading Failure If Edge Becomes Bottleneck

**Mitigation:**
- Load test before each phase
- Horizontal scale edge service
- Circuit breaker pattern for domain service calls

---

## Rollback Procedures

### Quick Rollback (5 minutes)

1. **Gateway traffic switch** (30 sec)
   - Route `/ws/*` back to service-local handlers
   - Or stop routing to edge entirely

2. **Client auto-reconnect** (varies)
   - Browsers typically reconnect within 30 seconds
   - Can push explicit disconnect to force faster reconnect

3. **Validation** (4 min)
   - Check error rates
   - Verify messages flowing normally
   - Confirm edge no longer receiving traffic

### Issues Found After Rollback

1. **Analyze logs** in edge-service and domain services
2. **Fix root cause** (usually HTTP client issue or event parsing bug)
3. **Deploy fix** to edge service
4. **Re-attempt migration** with extended monitoring

### Extended Rollback (If Data Corruption)

1. Keep edge-service running for investigation
2. Keep service-local handlers as primary
3. Never switch traffic back until confident in fix
4. May require data repair in domain services

---

## Monitoring & Alerts

### Per-Phase Monitoring

During each phase, monitor:

- **Latency:** p50, p99 message delivery time
- **Error Rate:** Failed commands, failed broadcasts
- **Throughput:** Messages/sec through edge
- **Resource Usage:** CPU, Memory in edge instances
- **Connection Count:** Active WebSocket connections
- **Queue Depth:** Kafka consumer lag, Redis queue sizes

### Critical Alerts

- Error rate > 0.1%
- Latency p99 > 500ms
- Message loss detected
- Consumer lag > 60 seconds
- Edge instance memory > 80%
- Network latency edge→domain > 50ms

### Success Metrics Per Phase

| Metric | Target |
|--------|--------|
| Error Rate | < 0.01% |
| Latency p99 | < 500ms |
| Message Loss | 0 |
| Availability | > 99.9% |
| Resource Usage | CPU < 60%, Mem < 70% |

---

## Timeline Summary

```
Week 1:  Phase A (Foundation) ✅ COMPLETE
Week 2-3: Phase B (Notification) → 2 weeks
Week 4-6: Phase C (Presence) → 2 weeks + 1 week soak
Week 7-10: Phase D (Chat) → 3 weeks + 1 week soak
Week 11-12: Phase E (Friendship) → 1.5 weeks
Week 13-16: Scale & Harden → 3 weeks

Total: ~16 weeks to full production migration
```

---

## Decision Gates

### Gate 1: Before Phase B Starts

- [ ] Phase A skeleton validated and deployed to staging
- [ ] CentralRealtimeWebSocketHandler tested with > 1000 concurrent sessions
- [ ] RestNotificationCommandRouter implementation planned
- [ ] Notification service team commits to HTTP endpoint

**Approval Required:** Tech lead + notification service lead

### Gate 2: Before Phase B Goes to Production

- [ ] Canary deployment successful (24h no errors)
- [ ] Load test shows acceptable latency
- [ ] Rollback plan verified
- [ ] Monitoring alerts configured

**Approval Required:** Tech lead + operations

### Gate 3: Before Phase C Starts

- [ ] Phase B running stable in production for 1 week
- [ ] 0 customer incidents attributed to edge migration

**Approval Required:** Tech lead

### Similar Gates Before Phases D, E

---

## Conclusion

This phase plan provides a **safe, incremental path** to realtime-edge-service migration.

**Key Principles:**
1. Each phase is **independent and reversible**
2. Extensive **testing before production**
3. **Gradual traffic shift** (10% → 50% → 100%)
4. **Easy rollback** if issues found
5. **Soak time** between phases for stability

**Start:** Phase B (Notification) can begin immediately after Phase A validated.

**Expected Outcome:** Full production migration in **12-16 weeks** with **0 customer impact** and **full rollback capability** at any phase.

---

**Prepared By:** Realtime Edge Migration Planning  
**Last Updated:** May 12, 2026  
**Next Review:** After Phase A validation  
**Next Phase:** Phase B (Notification) implementation begins
