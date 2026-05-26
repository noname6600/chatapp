# Realtime Edge Phase B - Staging Validation

**Status:** Staging validation passed for the notification edge slice, production canary not approved yet
**Date:** May 12, 2026
**Scope:** Notification migration only

---

## 1. Validation Decision

The Phase B notification slice is validated enough for a controlled staging rollout.

What was proven:
- notification commands route through the edge to notification-service over HTTP
- notification Redis events fan out through the edge to the right user sessions
- reconnect behavior works after a session is removed and a later delivery occurs
- a staging-sized in-process load smoke passed on the edge delivery path

What was not proven:
- notification-service runtime behavior behind the new HTTP command endpoint under an actual staging deployment
- multi-instance horizontal behavior
- soak behavior under real traffic

---

## 2. Validation Evidence

### Edge command routing
- `:realtime-edge-service:compileJava` passed
- `:realtime-edge-service:test` passed for the focused notification slice
- Verified route coverage:
  - `CommandDispatcherTest`
  - `RestNotificationCommandRouterTest`
  - `RedisEventListenerNotificationTest`
  - `NotificationRealtimeDeliveryServiceTest`
  - `RealtimeSessionRegistryTest`
  - `RealtimeEdgeApplicationTest`

### Load smoke
- Test: `NotificationRealtimeLoadValidationTest`
- Scenario:
  - 20 websocket sessions for one user
  - 200 delivery iterations
  - 4,000 total deliveries
- Observed result:
  - total elapsed: 980 ms
  - average per delivery: about 4.9 ms
  - dropped deliveries observed: 0

### Notification-service compile check
- `:notification-service:compileJava` passed
- The new command endpoint and DTO compile cleanly

---

## 3. What The Load Smoke Proved

The in-process load smoke showed that the edge delivery path can sustain a modest staging-sized fanout workload without drops.

Observed behavior:
- all 20 sessions stayed registered
- each delivery reached all open sessions in the test harness
- reconnect behavior remained correct when a session was removed and only the remaining session received later events

This is not a production throughput benchmark. It is a focused staging smoke on the delivery slice.

---

## 4. What Still Blocks Production Canary

- notification-service `test` cannot be used as a clean gate because existing unrelated test sources fail compilation before the new Phase B tests can run
- the edge session registry is still in-memory, so multi-instance behavior is not yet safe for rollout
- no real staging soak was performed against a deployed stack with Redis and Kafka in place
- the Kafka listeners in the edge tests still emit connection warnings when no broker is present, even though the notification-focused tests pass

---

## 5. Recommendation

Proceed with a staging-only rollout of the notification slice behind controlled routing.

Do not approve a production canary yet. Before that, the team should:
- run the notification-service endpoint against a real staging deployment
- rehearse rollback under live traffic
- add a distributed session strategy if horizontal scaling is part of the rollout plan
- collect soak metrics for latency, error rate, and delivery success
