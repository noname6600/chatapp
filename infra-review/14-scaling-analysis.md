# 14. Scaling Analysis

## 1) Horizontal Scaling Axes

### API scaling
- gateway is single logical ingress, can be horizontally scaled with shared Redis rate limit backend
- domain services scale independently by stateless request handling and isolated DBs

### Event scaling
- Kafka scales via partitions and consumer groups
- Redis pub/sub scales fanout, but not durability

### Realtime scaling
- realtime-edge can scale horizontally
- session ownership is tracked via Redis registry in redis mode

## 2) WebSocket Scaling With 5 Edge Servers

### Chat/presence/notification flows
1. publisher sends Redis pub/sub event once
2. all 5 edge instances receive pub/sub message
3. each edge delivers only to local owned sessions

This model avoids cross-instance query chatter for these flows.

### Friendship flow
1. Kafka event consumed by one edge consumer instance per partition ownership
2. local sessions delivered directly
3. remote sessions handed off via instance-targeted Redis handoff channels

This model trades extra coordination for controlled ownership-based delivery.

## 3) Session Registry Scaling
RedisRealtimeSessionRegistry key model supports:
- user->sessions lookup
- channel->sessions lookup
- instance->sessions lookup
- lease expiration and stale cleanup

Scaling limits:
- key cardinality grows with active sessions and subscriptions
- cleanup scan workload grows with session key volume

Tuneables:
- lease TTL
- cleanup interval
- cleanup batch size

## 4) Redis Capacity Considerations
Redis handles multiple workloads simultaneously:
- pub/sub transport
- session registry
- presence ephemeral state
- dedupe keys
- rate limiting (gateway)

This concentration creates contention risk at high scale. Separation by Redis cluster role may be needed in production-grade deployments.

## 5) Kafka Capacity Considerations
- throughput and parallelism depend on partition count per topic
- consumer concurrency in services is bounded by listener container config and partition assignments
- no explicit partition strategy documented in source; must be operationally defined

## 6) Database Scaling Considerations
- services own separate schemas/databases
- high write paths (chat messages, notifications) should scale independently
- chat sequence generation offloads some contention to Redis counter

## 7) Hot Path Bottlenecks
Potential bottlenecks under load:
- gateway route/filter chain and rate limiter Redis calls
- realtime-edge outbound queue and websocket session write throughput
- Redis pub/sub and keyspace operations under mixed workload
- notification fanout path for high-volume rooms

## 8) Consistency Vs Throughput Tradeoffs
Chosen architecture favors:
- high responsiveness and decoupling
- eventual consistency over strict global ordering

Tradeoff implications:
- easier horizontal scale with bounded consistency guarantees
- requires robust idempotency and reconciliation logic

## 9) Sticky Sessions Or Not
No strict sticky session requirement is visible for websocket correctness:
- session registry tracks ownership
- redis pub/sub broadcasts events to all instances

However, sticky sessions may still help reduce reconnect churn and improve cache locality.

## 10) Scaling Risk Checklist
- ensure Redis high availability and proper resource sizing
- enforce Kafka DLQ/retry consistency across services
- monitor websocket queue drop metrics and session cleanup metrics
- validate edge handoff channel throughput under friendship event spikes
- pressure-test keyspace notification behavior for presence at scale

## 11) Practical Scaling Strategy
1. scale gateway + realtime-edge separately from domain services
2. isolate Redis roles if utilization saturates
3. increase Kafka partitions for high-volume topics and align consumer groups
4. tune edge queue workers and capacity per session profile
5. add replay/reconciliation endpoints for client state repair after missed realtime windows
