# Realtime Edge Readiness After Redis Session Registry Foundation

## What This Unlocks

The edge now has a reusable multi-instance-ready session metadata layer:

- Global session/user/subscription indexes in Redis
- Explicit `instanceId` ownership per session
- Local-only websocket send semantics preserved
- Configuration switch between in-memory and Redis backend

This removes the hard single-instance metadata assumption from session ownership infrastructure.

## What Still Remains Before Presence Migration

Presence migration (Phase C) should not start until these are done:

1. Cross-instance event handoff
   - A presence event received on instance A must reach sessions owned by instance B
   - Requires edge-to-edge dispatch strategy (Redis pub/sub, stream, or queue coordination)

2. Session freshness/failover policy
   - Detect and clean stale sessions for crashed instances
   - Define heartbeat/lease and eviction behavior

3. Ownership-aware observability
   - Metrics split by global sessions vs local-owned sessions
   - Alerting on orphan/stale session growth

4. Consistency/replay decisions
   - Define behavior during redis/network blips and reconnect windows

## What Still Remains Before True Multi-Instance Rollout

This change alone is not full multi-instance rollout.

Still required:

- Cross-instance outbound event routing
- Backpressure strategy for high fanout traffic
- Redis/keyspace lifecycle hardening (TTL/cleanup strategy)
- Rolling restart behavior validation
- Failure-mode test matrix (instance kill, Redis failover, reconnect storms)

## Is Phase C the Right Next Coding Step?

Short answer: **not yet**.

Recommended next coding step before Phase C domain migration:

- Implement cross-instance dispatch coordination for edge delivery path.

Reason:

- Redis registry now tells us where sessions exist globally.
- But only owning instance can send websocket frames.
- Without cross-instance handoff, non-owning instances cannot complete delivery for remote-owned sessions.

## Current Readiness Statement

- Infrastructure status: **foundation ready**
- Multi-instance metadata: **implemented**
- Multi-instance delivery: **not implemented yet**
- Domain migration readiness (Presence): **defer until cross-instance dispatch is in place**

## Honest Scope Boundary

This change intentionally does **not**:

- migrate presence/chat/friendship/notification domains
- alter frontend behavior
- add staging/VPS rollout automation
- claim production multi-instance fanout completeness

It provides a stronger, reusable session ownership substrate for the next infrastructure step.
