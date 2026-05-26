# 04. Redis Core

## 1) Redis Roles In This System
Redis is used for four distinct categories:
1. realtime pub/sub transport
2. websocket session registry and ownership indexes
3. ephemeral presence state and TTL-driven offline semantics
4. cache acceleration and dedupe keys

This is important: Redis is not a single-purpose cache here. It is both transport and state plane.

## 2) Connection And Template Initialization
Low-level connection is provided by Spring Data Redis (RedisConnectionFactory).

Key templates:
- StringRedisTemplate in many services
- chat-service explicitly defines StringRedisTemplate bean in RedisAtomicConfig

Higher abstraction comes from common-redis auto-config:
- RedisEventPublisher
- RedisEventSerializer
- RedisEventRegistry

## 3) Canonical Channel Naming
From RedisChannels:
- realtime.chat.room.{roomId}
- realtime.notification.user.{userId}
- realtime.presence.user
- realtime.presence.global
- realtime.presence.room.{roomId}

These channel names are globally shared and consumed by realtime-edge Redis listener container.

## 4) Serialization And Contract Enforcement
JsonRedisEventSerializer behavior:
- parse JSON
- require metadata
- validate eventType
- resolve payload class from registry
- reject unknown payload-bearing events
- permit payload-less shared event types

DefaultRedisEventPublisher behavior:
- validate event metadata identity fields
- serialize envelope
- convertAndSend to channel
- throw RedisPubSubException on failures

Implication: Redis event publish can fail fast on invalid contracts, not silently.

## 5) Redis Keys By Concern

### Realtime edge session registry (redis mode)
- realtime:session:{sessionId} hash
- realtime:user:sessions:{userId} set
- realtime:subscription:sessions:{channel} set
- realtime:session:subscriptions:{sessionId} set
- realtime:instance:sessions:{instanceId} set
- realtime:users set
- realtime:session:count string

Lease TTL defaults to 90 seconds and is refreshed on activity.

### Handshake/auth bridging
- ws:ticket:{ticket} -> userId:accessToken (TTL 30s)
- ws:session-token:{tokenRef} -> accessToken (TTL 15m default)

### Presence ephemeral state
- presence::user:{userId} TTL state key
- presence::users:online set
- presence::room:{roomId} set
- presence::user:rooms:{userId} set
- presence::user:connections:{userId} counter

TTL examples in presence components:
- user TTL cache: 30 seconds
- ephemeral room/user-rooms/connections keys: 90 seconds

### Other keys
- room:seq:{roomId} message sequence counter
- room:access:{userId}:{roomId} authorization cache (30s)
- dedup:realtime:friendship:{eventId}
- dedup:notif:{eventId}

## 6) Pub/Sub Consumer Initialization
Realtime edge explicitly creates RedisMessageListenerContainer in RedisListenerConfig:
- pattern realtime.chat.room.*
- pattern realtime.notification.user.*
- pattern realtime.presence.*
- optional handoff channel pattern when enabled

Without this container, realtime-edge will not consume Redis pub/sub.

## 7) Redis In WebSocket Lifecycle
- ticket issued by RealtimeTicketController
- JwtHandshakeInterceptor validates ticket and creates tokenRef key
- RealtimeWebSocketHandler checks tokenRef key on connect/message
- if token ref missing/expired, socket is closed with TOKEN_EXPIRED error

This means websocket continuity depends on Redis key liveness.

## 8) Redis Cache Layer
User and presence use common-redis-cache TimeRedisCacheManager and TimeRedisCache.

Notable behavior:
- key prefix includes service name
- explicit TTL put operations
- tracks unavailable caches and deferred cleanup attempts

## 9) Redis For Atomic Operations
Chat message sequencing uses atomic increment on room:seq:{roomId} key.
- if key missing or increment fails, fallback computes from DB snapshot and resets key.

This gives low-latency monotonic per-room sequence assignment without DB sequence table contention.

## 10) TTL And Invalidation Strategy
- websocket sessions: lease refresh + scheduled stale eviction + orphan index cleanup
- room authorization cache: short TTL + explicit invalidation on member events
- dedupe keys: short TTL windows
- presence: TTL state with explicit cleanup on disconnect and fallback offline by TTL flow

## 11) Observed Design Strengths
- clear channel taxonomy
- shared serializer contract with event catalog
- lease-based distributed session ownership model
- short-lived auth bridging artifacts for websocket handshake

## 12) Observed Risks
- Redis pub/sub is non-durable, so transient subscriber outages lose events.
- Session token-ref expiry can terminate healthy-looking socket sessions if refresh path is not coordinated.
- Presence offline-by-expiry depends on keyspace notification plumbing being correctly wired.
- Redis is now critical to both transport and coordination, making it a high-blast-radius dependency.
