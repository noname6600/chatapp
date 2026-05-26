# Realtime Edge Readiness After Cross-Instance Dispatch

## What This Unlocks

The edge now moves from global metadata awareness to actual multi-instance delivery coordination:

- Local-owned websocket delivery still occurs directly.
- Remote-owned targets are now handed off per owner `instanceId`.
- Owning edge instance performs actual websocket send for those sessions.

This closes the previous delivery gap where non-owning instances could not complete remote delivery.

## What Still Remains Before Presence Migration

Presence domain migration still should not start until these are handled:

1. Session freshness/ownership cleanup
   - heartbeat/lease policy for stale instance/session metadata
   - deterministic orphan cleanup

2. Handoff resiliency
   - retry/backoff and optional durable queue semantics
   - dead-letter or failure accounting strategy

3. Ownership-aware observability
   - metrics for local vs remote handoff volume
   - success/failure dashboards and alerting

4. Consistency/replay decisions
   - behavior during redis/network outages
   - replay strategy for missed handoff events

## What Still Remains Before True Production Multi-Instance Rollout

This is a major infra step, but not full production hardening.

Still required:

- backpressure strategy for high fanout bursts
- stronger idempotency/duplicate controls across handoff paths
- failure-mode matrix validation (instance kill, Redis failover, reconnect storms)
- rolling restart validation under load
- operational SLOs and alarms for handoff pipeline

## Is Phase C the Right Next Coding Step?

Short answer: not yet.

Recommended next step:

- harden handoff reliability and ownership freshness before Presence migration.

Reason:

- dispatch coordination now exists and works by ownership.
- but reliability and stale-ownership controls are not complete enough for domain migration risk.

## Current Readiness Statement

- Infrastructure status: foundation plus cross-instance dispatch implemented
- Multi-instance metadata: implemented
- Multi-instance delivery coordination: implemented (handoff per owner instance)
- Production-grade delivery guarantees: not complete
- Presence migration readiness: defer until reliability/freshness hardening is completed

## Honest Scope Boundary

This change intentionally does not:

- migrate presence/chat/friendship/notification domain logic
- alter frontend behavior
- add staging/VPS rollout automation
- claim exactly-once or replay-safe distributed delivery

It provides the required edge infrastructure step to perform cross-instance websocket delivery handoff.
