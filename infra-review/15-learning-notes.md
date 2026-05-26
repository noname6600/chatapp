# 15. Learning Notes

## 1) Senior-Level Mental Model
Treat this system as three interacting planes:
1. command plane: synchronous HTTP/WebSocket command processing
2. durability plane: Kafka-backed eventual consistency events
3. experience plane: Redis-backed realtime fanout

Most hard bugs happen at plane boundaries, not inside single services.

## 2) Redis Vs Kafka In Practice

Redis pub/sub:
- great for fast fanout
- weak durability guarantees
- no replay
- ideal for typing, presence, instant UI updates

Kafka:
- durable event log
- replay and consumer groups
- ideal for downstream business effects and long-lived consistency

Staff-level takeaway: use both intentionally, never assume they provide equivalent guarantees.

## 3) Why After-Commit Publishing Appears Everywhere
Code uses TransactionSynchronization.afterCommit wrappers because publishing before commit can create phantom events.

Pattern advantage:
- no event emitted for rolled-back transaction

Pattern limitation:
- DB commit and broker publish are still not atomically bound
- if publish fails post-commit, event loss remains possible

Outbox/CDC is the next maturity step if strict reliability is required.

## 4) Why Realtime Edge Is Separate
Centralizing websocket ingress avoids every domain service owning:
- socket lifecycle
- channel auth and subscription state
- fanout mechanics

This separation improves team focus and independent scaling, but increases edge complexity and operational importance.

## 5) What Makes This Architecture Production-Like
- clear bounded context services
- shared event contract catalog
- gateway policy layer
- dedicated realtime edge
- explicit cleanup jobs and lease concepts

These are patterns used in many mature event-driven systems.

## 6) What Keeps It From Top-Tier Reliability Today
- no universal outbox/transactional messaging strategy
- uneven Kafka retry/DLQ policy adoption
- Redis pub/sub loss windows for realtime
- presence expiry path depends on keyspace notification plumbing

## 7) How To Reason About Guarantees
Ask these questions for every flow:
1. is this state change durable?
2. is this update replayable?
3. what happens if consumer is down?
4. what if producer succeeds in DB but fails in broker?
5. can duplicate events be safely applied?

If answers differ across flows, document that explicitly for engineers and product stakeholders.

## 8) Skills To Build Next (For Senior Growth)
- event contract evolution and compatibility testing
- idempotent consumer design
- outbox/CDC and delivery guarantees
- distributed tracing across sync+async chains
- capacity planning for Redis + Kafka + websocket fanout
- incident response for partial-failure distributed systems

## 9) Suggested Exercises In This Repo
1. Trace one eventType from producer to websocket payload and note every failure point.
2. Simulate Redis outage and document exact UX degradation path.
3. Simulate Kafka consumer exception and inspect DLQ behavior per service.
4. Compare in-memory vs redis session-registry mode behavior under multi-instance edge deployment.
5. Add a new event type in common-events mentally and list all required producer/consumer changes.

## 10) Principal Engineer Heuristic
Design for partial failure as the default, not as an exception.

In this system:
- Redis path gives speed
- Kafka path gives durability
- API path gives authoritative reads

Production readiness is mostly about making these three agree under failure, lag, and reconnect.
