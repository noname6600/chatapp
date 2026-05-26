# 15. Learning Notes

## Why Senior Teams Design It This Way

## Separate Auth and Profile
Industry reason:
- identity lifecycle and profile lifecycle change at different speeds.
- blast radius reduction for sensitive auth changes.

Tradeoff:
- requires async sync (account-created events) and eventual profile readiness handling.

## Gateway-Centric Ingress
Industry reason:
- one place for CORS, auth, retries, rate limiting, and route policy.

Tradeoff:
- gateway can become a critical choke point and policy monolith.

## Kafka + Redis Split
Industry reason:
- Kafka for durable/eventual consistency.
- Redis pub/sub for low-latency fanout.

Tradeoff:
- dual-plane complexity; teams must understand guarantee mismatch.

## Realtime Edge Service
Industry reason:
- avoid embedding websocket lifecycle complexity into each business service.
- easier horizontal websocket scaling and protocol evolution.

Tradeoff:
- edge service must understand enough domain routing semantics to dispatch correctly.

## Pipeline Pattern in Chat Commands
Industry reason:
- deterministic step ordering and testable composition.
- clean insertion points for validation, persistence, event publication.

Tradeoff:
- more indirection than direct service methods; onboarding requires flow familiarity.

## Correlation IDs and Envelope Metadata
Industry reason:
- distributed tracing and debugging across async boundaries.

Tradeoff:
- requires strict propagation discipline in every producer/consumer.

## Senior-Level Interview Explanation
"This system is a domain-oriented microservice architecture with centralized gateway ingress and dedicated realtime edge. Durable domain propagation is Kafka-based; websocket fanout and ephemeral state are Redis-assisted. Contracts are standardized with event envelopes and shared payload catalog, while services maintain local data ownership."

## Production-Level Explanation
"The design optimizes team autonomy and realtime user experience but increases operational complexity. Correctness depends on clear contract governance, idempotent event handling, strong observability, and disciplined migration strategy. Reliability under failure requires explicit handling for partial durability and replay gaps."

## What to Watch as Owner
- event schema drift and compatibility
- token and internal trust-path policies
- redis and kafka health as first-class SLO dependencies
- gateway policy regression tests
- replay/reconciliation behavior after disconnects
