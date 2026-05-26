# Redis Review

## Findings (ordered by severity)

### 1) HIGH - Notification service container env does not match Redis property keys
- Severity: HIGH
- Exact location: `notification-service/src/main/resources/application.yaml` expects `SPRING_DATA_REDIS_HOST/PORT`; `docker-compose.yml` and `docker-compose.local.yml` set `REDIS_HOST/REDIS_PORT` for notification-service
- Root cause: Environment variable naming mismatch.
- Impact: Service can default to localhost Redis inside container and fail runtime Redis operations.
- Reproduction risk: High in compose deployments.
- Scalability risk: High (notification fanout and dedupe degrade).
- Recommended fix direction: Align runtime env variable names with Spring property binding.
- Shared/common changes required: No

### 2) HIGH - Room authorization cache in edge can permit stale membership window
- Severity: HIGH
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/subscription/ChannelSubscriptionManager.java` (`ROOM_ACCESS_CACHE_TTL = 60s`)
- Root cause: Cached room-access authorization with fixed TTL.
- Impact: User removed from room can remain authorized for short window.
- Reproduction risk: Medium.
- Scalability risk: High in large rooms/high churn.
- Recommended fix direction: Add invalidation signal on membership mutation or tighten cache strategy.
- Shared/common changes required: No

### 3) MEDIUM - Redis keyspace behavior differs between compose profiles
- Severity: MEDIUM
- Exact location: `docker-compose.yml` sets Redis command `--notify-keyspace-events Kx`; `docker-compose.local.yml` does not set equivalent command
- Root cause: Inconsistent Redis runtime flags across deployment profiles.
- Impact: Behavior drift for expiration/keyspace-dependent logic.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Standardize Redis server behavior across supported profiles.
- Shared/common changes required: No

### 4) MEDIUM - Redis-backed session registry uses set/hash scans for cleanup
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/connection/RedisRealtimeSessionRegistry.java` (`evictStaleSessions`, `cleanupOrphanIndexes`)
- Root cause: Periodic scan-based cleanup in primary Redis namespace.
- Impact: Increased Redis CPU during heavy session cardinality.
- Reproduction risk: Medium at scale.
- Scalability risk: High for very large fanout deployments.
- Recommended fix direction: Keep cleanup bounded and monitor scan latency/key cardinality.
- Shared/common changes required: No

### 5) MEDIUM - Redis listener toggles are unevenly enforced
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/config/RedisListenerConfig.java` gated by `realtime.redis.listener.enabled`; `realtime-edge-service/src/main/resources/application.yaml` sets enabled true by default
- Root cause: Listener activation depends on property discipline per environment.
- Impact: Misconfigured env can silently disable realtime pub/sub ingestion.
- Reproduction risk: Medium.
- Scalability risk: High (cross-node delivery impact).
- Recommended fix direction: Add startup assertions/health indicators for listener activation.
- Shared/common changes required: No

### 6) LOW - Dedupe key naming is service-specific and not centrally governed
- Severity: LOW
- Exact location: `notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationEventDedupeGuard.java` (`dedup:notif:`)
- Root cause: Prefix schemes are local conventions without obvious cross-service governance.
- Impact: Harder operational observability when triaging dedupe behavior across services.
- Reproduction risk: Low.
- Scalability risk: Medium.
- Recommended fix direction: Define key namespace governance and observability tags.
- Shared/common changes required: Potentially Yes (if centralized), but can start locally
