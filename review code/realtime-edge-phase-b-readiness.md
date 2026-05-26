# Realtime Edge Phase B - Notification Readiness

**Status:** Functionally ready for staging validation, not ready for production rollout  
**Date:** May 12, 2026  
**Scope:** Notification migration only

---

## 1. Readiness Decision

**Notification migration is functionally ready in staging.**

The first notification slice is now wired end-to-end:
- websocket client can send notification commands to the edge
- edge forwards supported commands to notification-service over HTTP
- notification-service executes the command through existing domain services
- Redis notification events are consumed by the edge and fanned out to subscribed sessions

That said, this is still **not production-rollout ready**.

---

## 2. What Is Ready

### Edge routing
- Notification commands can be routed from websocket client through the edge dispatcher to `RestNotificationCommandRouter`
- Supported commands are limited to the command set already backed by `NotificationCommandService`

### Event delivery
- Edge consumes the existing Redis notification transport
- Edge fans out `RealtimeWsEvent` payloads to the correct user-scoped websocket sessions

### Identity handling
- The websocket handshake already extracts the user identity from the JWT-based auth flow
- The same access token is forwarded to notification-service for command execution

### Rollback
- Existing notification websocket handling in notification-service is still available
- Traffic can be routed back without removing the new endpoint or listener

---

## 3. What Still Blocks Production Rollout

### 3.1 Load and soak testing
The Phase B path has not yet been exercised under realistic load.

**Must test:**
- websocket connect/disconnect volume
- per-user notification fanout
- command forwarding latency
- Redis listener throughput
- notification-service HTTP timeout behavior

### 3.2 Multi-instance session ownership
The edge session registry is still in-memory.

**Blocking issue:**
- a second edge instance would not see the first instance's session state
- notification fanout would need a distributed registry before horizontal scale cutover

### 3.3 Contract growth risk
The command set is intentionally small and explicit.

**Blocking issue:**
- if notification product behavior expands, the request model and controller contract may need revision
- that is acceptable for staging, but should be reviewed before broad rollout

### 3.4 End-to-end observability
Logs and metrics are present, but not yet proven in a rollout setting.

**Blocking issue:**
- no soak-based evidence yet for error rate, Redis lag, or HTTP retry behavior
- no cutover guardrails have been validated under traffic

---

## 4. What Should Be Load Tested

### Command forwarding
- `mark-read`
- `mark-all-read`
- `clear-room`
- `mark-read-by-room`

### Event delivery
- notification.new
- notification.unread.count.updated
- fanout to multiple sessions for the same user
- reconnect behavior after a session closes and reopens

### Scale characteristics
- concurrent websocket sessions
- Redis pub/sub throughput for notification channels
- latency between notification-service publish and edge delivery
- HTTP timeout and error handling when notification-service is slow or unavailable

### Failure behavior
- notification-service temporarily unavailable
- Redis listener delayed or disconnected
- websocket session closed while event is in flight

---

## 5. Metrics and Logs to Monitor

### Edge metrics
- active websocket sessions
- notification command dispatch count
- notification command failure count
- Redis notification listener lag
- notification delivery success/failure count
- command round-trip latency

### Notification-service metrics
- command endpoint request count
- command endpoint error count
- unread notification count updates
- notification publish count

### Logs to watch
- edge command routing failures
- HTTP timeouts from `RestNotificationCommandRouter`
- Redis notification payload parse errors
- user/channel mismatches in delivery
- unauthorized or missing-token handshake failures

---

## 6. Readiness Summary

### Staging
**Ready with the following expectations:**
- feature-flagged or controlled routing to the edge slice
- focused validation of notification commands and delivery only
- no attempt to migrate other domains in the same release

### Production
**Not ready yet.**

Before production rollout, the team should complete:
- load testing
- soak testing
- multi-instance session strategy
- alert tuning
- rollback rehearsal under traffic

---

## 7. Recommendation

Proceed with a staged validation plan:
1. Enable the edge notification slice in a staging environment
2. Validate command forwarding and Redis fanout
3. Measure latency and error rate
4. Rehearse rollback to the service-local notification websocket path
5. Only then consider production rollout

---

**Prepared By:** Realtime Edge Phase B Readiness Review  
**Last Updated:** May 12, 2026
