# 14. Risk Analysis

## Risk Ranking Legend
- Critical: immediate production-impact potential with security/data/reliability consequences.
- High: likely instability or correctness issue under scale/failure.
- Medium: manageable but accumulative reliability/maintainability concern.
- Low: quality debt with limited immediate blast radius.

## Critical Risks
1. Non-durable realtime path dependency
- Evidence: heavy Redis pub/sub usage for realtime fanout.
- Impact: dropped realtime events without replay.
- Affected: chat/presence/notification UX consistency.

2. Schema governance gap
- Evidence: `ddl-auto: update` in checked service configs.
- Impact: uncontrolled schema drift, risky deploy rollbacks, audit difficulty.

## High Risks
1. Mixed authorization trust paths
- JWT at gateway and services plus internal token filters in select services.
- Misconfiguration can open internal endpoint exposure.

2. Uneven consumer failure handling
- Some kafka consumers only log and continue.
- Poison messages and repeated failures may be under-observed.

3. Realtime subscription authorization cache staleness
- Explicit stale window accepted in `ChannelSubscriptionManager`.
- Can briefly permit outdated room access.

4. Token lifecycle tied to Redis key liveness for websocket sessions
- Session close behavior depends on token-ref key continuity.

## Medium Risks
1. Common module breadth creates coordinated release burden.
2. Dedupe/idempotency patterns are not uniformly applied.
3. Gateway resilience/routing policy complexity may drift from service realities.

## Low Risks
1. Naming/style inconsistencies across modules.
2. Migration-era comments/classes increase cognitive load.

## Highest Priority Mitigations
1. Define durable replay/catch-up path for realtime-critical user events.
2. Replace `ddl-auto` runtime evolution with explicit migrations.
3. Standardize Kafka consumer retry + DLQ + alert contracts.
4. Formalize internal endpoint trust model and rotate internal service tokens.
