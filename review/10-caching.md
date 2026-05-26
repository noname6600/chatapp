# 10. Caching

## Why Redis Exists
Redis is used for four distinct concerns:
1. ephemeral presence state
2. realtime pub/sub fanout
3. short-lived authorization/session artifacts (websocket tickets, token refs)
4. application cache acceleration (user/profile/room-related caches)

## Cache Strategy by Domain
User service:
- uses `common-redis-cache` manager for profile-related caches.
- invalidation hooks happen around profile update paths.

Chat service:
- uses Redis increment for room message sequence (`room:seq:{roomId}`) with DB fallback.
- publishes realtime room events via Redis channels.

Presence service:
- keeps online users, room users, user-room sets, and connection counters in Redis with TTL.

Realtime edge:
- stores ticket and session-token references.
- optional redis-backed distributed session registry.

## TTL Strategy
Observed TTL examples:
- websocket ticket: 30s
- session-token ref: ~15m default
- presence ephemeral keys: ~90s
- room access authorization cache in edge: ~30s

Tradeoff:
- short TTL reduces stale/compromise window but increases churn and refresh pressure.

## Invalidation Strategy
- explicit invalidation in service logic (for block cache, user cache updates).
- TTL-based natural expiration for ephemeral state.
- session registry cleanup jobs evict stale sessions and orphan indexes.

## Distributed Locking
No broad distributed lock framework detected as a first-class pattern; atomic Redis operations and DB constraints are preferred.

## Websocket Scaling Support
Redis supports:
- pub/sub distribution of realtime events
- distributed session registry mode in edge
- ownership split/handoff patterns for cross-instance dispatch

## Risks
1. Redis pub/sub is not durable; subscriber lag or disconnect can drop transient events.
2. Authorization cache staleness window can temporarily allow outdated room access decisions.
3. Invalidation logic is multi-service and multi-layer; weak observability can hide cache inconsistency.

## Recommendations
- Standardize cache key naming docs and ownership.
- Add cache hit/miss/eviction dashboards per service.
- Define strict list of security-sensitive decisions that must bypass cache under certain conditions.
