# 06. Websocket Flow

## Ingress Path
1. FE requests ticket via `POST /api/v1/realtime/ticket` with bearer token.
2. Gateway routes to realtime-edge HTTP endpoint.
3. `RealtimeTicketController` extracts user id from JWT principal and stores Redis ticket (`ws:ticket:*`, TTL 30s).
4. FE opens websocket `ws://.../ws/realtime?ticket=...` (`realtime.socket.ts`).
5. Gateway rewrites `/ws/**` to `/realtime` and forwards to realtime-edge ws route.
6. `JwtHandshakeInterceptor` validates ticket, deletes one-time ticket, writes `ws:session-token:{ref}` key.
7. `RealtimeWebSocketHandler.afterConnectionEstablished` registers session and default subscriptions.

## Session Registry
Two modes:
- in-memory (`InMemoryRealtimeSessionRegistry`) for local/single-node.
- redis (`RedisRealtimeSessionRegistry`) for distributed mode.

Redis mode tracks:
- session hashes
- user->sessions sets
- channel->sessions sets
- instance->sessions sets
- lease expiry and stale cleanup

## Message Routing
`RealtimeWebSocketHandler.handleTextMessage`:
- validates active access token reference
- presence commands (`presence.*`) -> presence domain client
- chat commands (`JOIN`, `SEND`, `EDIT`, `DELETE`, `REACTION`, `PIN`, `UNPIN`) -> chat command router
- generic subscribe/unsubscribe/ping commands via dispatcher

## Subscription Authorization
`ChannelSubscriptionManager` authorizes channels:
- room/presence/typing: checks room access via chat command router
- user/notification/friendship: self-only semantics by user id
- room access results cached in Redis (short TTL)

## Delivery Pipeline
Inbound domain events (Kafka/Redis) -> delivery service -> local owned sessions -> outbound queue -> websocket frame.

Delivery services:
- `ChatRealtimeDeliveryService`
- `PresenceRealtimeDeliveryService`
- `NotificationRealtimeDeliveryService`
- `FriendshipRealtimeDeliveryService`

Cross-instance support:
- friendship delivery uses ownership split + handoff publisher/coordinator.
- other flows commonly rely on each instance receiving pub/sub and delivering only locally owned sessions.

## Presence-Specific Lifecycle
- on connect: edge submits presence connect side effect.
- heartbeat commands renew activity.
- room join emits room snapshot fetch.
- disconnect triggers presence disconnect side effect and cleanup.

## Guarantees
What is strong:
- session-level cleanup and token-expiry closure implemented.
- local delivery order per session is serialized by outbound queue intent.

What is weak:
- strict global ordering across instances is not guaranteed.
- Redis pub/sub is non-durable (event loss possible during subscriber issues).
- exactly-once delivery is not guaranteed.

## Notable Risks
- handshake stores access token reference in Redis; TTL/lifecycle mismatch can disconnect valid clients.
- room authorization cache intentionally allows brief stale window.
- reconnect storms can create burst load on ticket issuance and subscription re-joins.
