# 13. Failure And Recovery

## 1) Redis Failure Modes

### Case A: Redis unavailable during publish
Affected classes:
- DefaultRedisEventPublisher
- ChatRedisPublisher / PresenceRedisPublisher / RedisNotificationPublisher

Behavior:
- publish throws RedisPubSubException from common-redis
- upstream handlers may catch/log and continue depending on call site

Impact:
- realtime fanout gaps
- websocket clients miss live updates

Recovery:
- clients eventually reconcile via API fetches
- no replay from Redis pub/sub itself

### Case B: Redis subscriber disconnect on edge
Affected:
- RedisMessageListenerContainer in realtime-edge

Behavior:
- published messages while subscriber unavailable are lost to that instance
- once reconnects, future messages resume

Impact:
- sessions on that edge instance miss event window

### Case C: Redis keyspace notifications misconfigured
Affected:
- presence offline-by-TTL semantics

Behavior:
- startup checker warns/fails based on config
- TTL expiration events may never fire

Impact:
- stale online presence state risk

## 2) Kafka Failure Modes

### Producer failure after DB commit
Because event publication often runs after commit, a broker outage can cause:
- persisted domain state exists
- async event not published

Impact:
- downstream services do not converge
- notifications/realtime side effects can be missing

### Consumer processing failure
With configured handlers:
- retries (fixed or exponential)
- then DLQ route (system.dead-letter)

Without standardized handlers:
- behavior varies by service
- potential under-observed repeated failures

## 3) WebSocket Edge Failure Modes

### Instance crash
- in-memory session store lost immediately
- redis registry may retain stale entries until cleanup
- clients reconnect to other instance

### Token-ref expiration
- requireActiveAccessToken fails
- connection closes with TOKEN_EXPIRED
- client must re-ticket and reconnect

### Outbound queue overflow
- enqueue fails
- drop metric increments
- message dropped for that session

## 4) Race Conditions To Understand

### Session lease race
- local websocket appears active
- redis lease expires due to missed refresh
- registry thinks session stale and may evict

### Membership cache stale window
- ChannelSubscriptionManager caches room access for 30s
- immediate post-leave/ban updates may be delayed by cache window
- invalidation on member events reduces but may not eliminate race windows

### Dedupe window limitations
- dedupe keys expire (5 min in some guards)
- very-late duplicates can reapply if outside window

## 5) Duplicate And Lost Event Matrix

Lost event risks:
- Redis pub/sub subscriber downtime
- afterCommit publish failure without outbox replay
- websocket disconnect window

Duplicate risks:
- retried processing paths
- re-delivery after rebalance/restart
- dual path migration interactions

Mitigations in code:
- eventId propagation
- dedupe guards in select consumers
- bounded queue serialization per session

## 6) Recovery Patterns Present
- Kafka retry + DLQ in user/notification configs
- stale session cleanup and orphan index cleanup in realtime-edge
- fallback to durable API reads for UI synchronization
- startup sanity checks (presence keyspace config)

## 7) Recovery Patterns Missing Or Incomplete
- no universal outbox for exactly-once DB-event publication coupling
- no uniform replay toolchain surfaced in code for DLQ reprocessing
- no explicit global ordering reconciliation protocol for realtime events

## 8) Operational Playbook Guidance
When incidents occur:
1. verify Redis connectivity and listener container health on realtime-edge
2. verify Kafka consumer lag and DLQ topic growth
3. inspect edge session cleanup metrics and queue drop counters
4. force client resync through API refresh if realtime window was missed
5. validate internal auth header token configs across presence/friendship services

## 9) Recovery Priority Principles
- Preserve durable correctness first (Kafka + DB consistency)
- Degrade realtime UX second (Redis/WebSocket path)
- Reconcile client state deterministically after transient disruptions
