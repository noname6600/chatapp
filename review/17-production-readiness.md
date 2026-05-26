# 17. Production Readiness

## Current Readiness Posture
The system is close to production-capable in architecture, but several control-plane and reliability practices need hardening for robust ownership at scale.

## Reliability
Strengths:
- gateway retries/circuit breakers/readiness checks
- domain separation limits blast radius
- after-commit event publishing in key write paths

Gaps:
- inconsistent consumer retry/DLQ standards
- non-durable realtime fanout gaps
- mixed cleanup logic across session/cache layers

## Scalability
Strengths:
- services can scale independently
- dedicated realtime edge supports websocket scale-out
- Redis-backed distributed session registry option

Gaps:
- cross-instance ordering and dedupe semantics are partial
- gateway rate-limiter and redis dependency become hot path bottlenecks under burst

## Security
Strengths:
- JWT validation at multiple layers
- refresh token hashing + rotation
- websocket ticket pattern improves browser handshake security

Gaps:
- internal trust model complexity
- secret default hygiene risk
- localStorage token storage XSS sensitivity

## Observability
Strengths:
- trace id propagation and response header
- actuator endpoints and readiness indicator

Gaps:
- event-level SLO and failure visibility not uniformly codified
- no single standardized tracing recipe for async replay debugging

## Data Governance
Strengths:
- practical indexing for high-value query paths

Gaps:
- migration governance weak due to `ddl-auto`.
- schema drift risk across environments.

## Operations Checklist (Must-Have)
1. enforce migration tooling (Flyway/Liquibase) and disable runtime schema auto-update.
2. define kafka consumer error contract: retry, DLQ, poison handling, alert thresholds.
3. define realtime catch-up strategy after websocket disconnect.
4. define secure secret injection and internal token rotation procedures.
5. establish load-test baselines for gateway, edge websocket, redis, and kafka paths.

## Provisional Scores (0-10)
- Architecture: 8.0
- Maintainability: 7.2
- Scalability: 7.6
- Security: 7.0
- Realtime Architecture: 7.4
- Code Consistency: 7.1
- Production Readiness: 6.8

Interpretation:
Strong architecture foundation with moderate operational and consistency debt.
