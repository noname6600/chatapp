# 18. Improvement Ideas

## Highest ROI Improvements (Near Term)
1. Replace `ddl-auto: update` with explicit migrations in all services.
2. Standardize Kafka consumer reliability profile:
   - per-topic retry policy
   - DLQ routing
   - poison-message quarantine + replay tooling
3. Add realtime catch-up API contract for missed event windows.
4. Tighten internal endpoint auth model and remove default weak tokens.
5. Add gateway and realtime-edge policy regression tests as release gates.

## Medium-Term Improvements
1. Unify idempotency and dedupe utilities in common modules.
2. Add per-flow observability dashboards:
   - login success/failure and refresh replay incidents
   - message send->realtime latency percentile
   - presence convergence lag
3. Reduce common module sprawl by splitting infra and policy concerns.
4. Formal schema/version compatibility tests for `common-events` payload changes.

## Long-Term Architecture Options
1. Durable realtime event journal for guaranteed replay on reconnect.
2. Centralized policy engine for authz decisions across services.
3. Per-device session management and revocation UX in auth domain.

## Biggest Strengths
- domain boundaries are clear
- event contracts centralized
- realtime edge direction is strategically correct

## Biggest Weaknesses
- reliability standards not uniform across consumers
- dual-plane realtime durability complexity
- migration governance debt

## Most Dangerous Technical Debt
- implicit schema evolution in running environments
- silent event-loss risk on transient realtime channels

## Hardest Parts to Maintain
- cross-service flow correctness under partial failures
- websocket + kafka + redis convergence guarantees

## Easiest Improvement Wins
- migration tooling adoption
- standardized consumer error framework
- clear internal endpoint auth hardening

## Final Architecture Summary Scores
- Architecture score: 8.0/10
- Maintainability score: 7.2/10
- Scalability score: 7.6/10
- Security score: 7.0/10
- Realtime architecture score: 7.4/10
- Code consistency score: 7.1/10
- Production readiness score: 6.8/10
