# 11. Runtime Sequence Diagrams

## 1) WebSocket Connect With Ticket
```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway
    participant RE as Realtime Edge
    participant R as Redis
    participant P as Presence Service

    C->>GW: POST /api/v1/realtime/ticket (Bearer JWT)
    GW->>RE: Forward
    RE->>R: SET ws:ticket:{ticket}=userId:token TTL 30s
    RE-->>C: {ticket}

    C->>GW: WS /ws/realtime?ticket=...
    GW->>RE: Rewrite to /realtime
    RE->>R: GET ws:ticket:{ticket}
    RE->>R: DEL ws:ticket:{ticket}
    RE->>R: SET ws:session-token:{ref}=token TTL 15m
    RE->>RE: register RealtimeSession + subscriptions
    RE->>P: POST /presence/ws/connect (Bearer token)
    P-->>RE: 200
    RE->>P: GET /presence/global snapshot
    RE-->>C: presence.global.snapshot
```

## 2) Send Message Full Path
```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway
    participant CH as Chat Service
    participant DB as PostgreSQL
    participant R as Redis
    participant K as Kafka
    participant RE as Realtime Edge
    participant N as Notification Service

    C->>GW: POST /api/v1/messages
    GW->>CH: Forward with JWT/X-User-Id
    CH->>DB: Persist message tx
    CH->>CH: Register afterCommit publish
    DB-->>CH: Commit
    CH->>R: PUBLISH realtime.chat.room.{roomId}
    CH->>K: PRODUCE chat.message.sent
    CH-->>C: 200 MessageResponse

    R->>RE: Pub/Sub event
    RE-->>C: WS chat.message.sent

    K->>N: Consume chat.message.sent
    N->>DB: Insert notification
    N->>R: PUBLISH realtime.notification.user.{userId}
    R->>RE: Notification pub/sub
    RE-->>C: WS notification.new
```

## 3) Typing Event Path
```mermaid
sequenceDiagram
    participant C as Client
    participant RE as Realtime Edge
    participant P as Presence Service
    participant R as Redis
    participant RE2 as Other Edge Instances

    C->>RE: WS presence.room.typing
    RE->>P: POST /presence/ws/rooms/{roomId}/typing
    P->>R: PUBLISH realtime.presence.room.{roomId}
    R->>RE: Deliver pub/sub
    R->>RE2: Deliver pub/sub
    RE-->>C: WS presence.room.typing
    RE2-->>C: WS to their local subscribers
```

## 4) Friendship Kafka To Cross-Instance Delivery
```mermaid
sequenceDiagram
    participant F as Friendship Service
    participant K as Kafka
    participant RE1 as Realtime Edge Instance A
    participant RE2 as Realtime Edge Instance B
    participant R as Redis
    participant C as Client

    F->>K: PRODUCE friendship.request.events
    K->>RE1: @KafkaListener consume
    RE1->>RE1: Split local vs remote session ownership
    RE1-->>C: WS for local-owned sessions
    RE1->>R: PUBLISH realtime.edge.handoff.{instanceB}
    R->>RE2: handoff event
    RE2-->>C: WS for sessions on instance B
```

## 5) Presence Offline By TTL
```mermaid
sequenceDiagram
    participant P as Presence Service
    participant R as Redis
    participant RE as Realtime Edge
    participant C as Client

    P->>R: SET presence::user:{userId} TTL
    Note over R: key expires
    R-->>P: keyevent expired (expected)
    P->>P: handleUserOfflineByTTL
    P->>R: PUBLISH realtime.presence.user
    R->>RE: pub/sub delivery
    RE-->>C: WS presence.user.offline
```

## 6) Gateway Security + Route Pipeline
```mermaid
sequenceDiagram
    participant C as Client
    participant GW as Gateway
    participant S as Target Service

    C->>GW: HTTP request
    GW->>GW: SecurityWebFilterChain JWT validation
    alt protected route
        GW->>GW: JwtAuthFilter adds X-User-Id
    end
    GW->>GW: Retry/CircuitBreaker/RateLimit filters
    GW->>S: Forward
    S-->>GW: Response
    GW-->>C: Response
```
