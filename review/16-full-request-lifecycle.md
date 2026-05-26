# 16. Full Request Lifecycle

This section traces requested flows with the 10-step model:
1) entry
2) validation
3) auth
4) service calls
5) DB ops
6) cache ops
7) broker ops
8) websocket broadcast
9) response
10) error handling

## A. Login
1. Entry: FE `auth.service.ts` -> `POST /api/v1/auth/login`.
2. Validation: request DTO validation in `AuthController`.
3. Auth checks: credential check in `LocalAuthService.login`.
4. Service calls: `AuthService.login` -> `AuthSessionService.issueTokens`.
5. DB ops: account lookup, refresh token row insert.
6. Cache ops: none required.
7. Broker ops: none on plain login.
8. Websocket: FE later connects realtime socket using issued access token ticket flow.
9. Response: token pair in `AuthResponse`.
10. Errors: invalid credentials -> unauthorized business exception.

## B. Register
1. Entry: `POST /api/v1/auth/register`.
2. Validation: DTO + duplicate email logic.
3. Auth checks: none pre-auth.
4. Service calls: `LocalAuthService.register` and idp link.
5. DB ops: account insert (or existing account reuse path).
6. Cache ops: none.
7. Broker ops: account-created event published after commit.
8. Websocket: none directly.
9. Response: token pair returned.
10. Errors: conflict or validation errors from duplicate/inconsistent credentials.

## C. Refresh Token
1. Entry: FE axios refresh interceptor -> `/auth/refresh`.
2. Validation: refresh token string presence.
3. Auth checks: hash lookup + revoked/expiry checks.
4. Service calls: `TokenServiceFacade.refresh`.
5. DB ops: atomic revoke old token + insert new token.
6. Cache ops: FE localStorage updated.
7. Broker ops: none.
8. Websocket: existing socket may continue until token-ref expiry; FE reconnect path handles renewal.
9. Response: new token pair.
10. Errors: invalid/revoked/expired -> 401 and logout event in FE.

## D. Websocket Connect
1. Entry: FE `requestRealtimeTicket` then `new WebSocket(/ws/realtime?ticket=...)`.
2. Validation: ticket existence/format in `JwtHandshakeInterceptor`.
3. Auth checks: JWT principal derived from ticket payload.
4. Service calls: handler registers session and side-effect connect call to presence domain.
5. DB ops: none.
6. Cache ops: Redis ticket delete, session-token key write.
7. Broker ops: indirect via subsequent presence publish.
8. Websocket broadcast: ack/events to client once subscribed.
9. Response: open connection (protocol-level success).
10. Errors: invalid ticket -> handshake reject; expired token-ref -> policy close.

## E. Send Message
1. Entry: FE sends REST `POST /api/v1/messages` (and websocket SEND path for realtime command support).
2. Validation: DTO validation + block/mention filtering in `MessageCommandService` pipeline.
3. Auth checks: sender from JWT subject.
4. Service calls: send pipeline steps and room/service guards.
5. DB ops: message insert, attachments/mentions inserts, room projection update.
6. Cache ops: sequence increment in Redis (`room:seq:*`).
7. Broker ops: after commit publish to Redis room channel and Kafka topic.
8. Websocket broadcast: realtime-edge delivery to room subscribers.
9. Response: `MessageResponse` from API.
10. Errors: room access, block, validation, or persistence failures with mapped business errors.

## F. Typing Event
1. Entry: FE sends `presence.room.typing` over realtime socket.
2. Validation: room id parsing and channel membership checks.
3. Auth checks: token-ref active + room authorization.
4. Service calls: edge -> presence command endpoint.
5. DB ops: none.
6. Cache ops: ephemeral presence/room key updates where applicable.
7. Broker ops: presence redis publish event.
8. Websocket broadcast: edge presence delivery service fans typing event.
9. Response: no HTTP body; event propagation is observable via incoming websocket events.
10. Errors: malformed frame or unauthorized room -> error frame.

## G. Notification Event
1. Entry: triggered by kafka event (chat/friend/account).
2. Validation: envelope null checks and payload checks in consumers.
3. Auth checks: internal service context, no user interactive auth.
4. Service calls: notification application service handles event.
5. DB ops: notification row insert/update read state.
6. Cache ops: room mute checks/cached lookups if configured.
7. Broker ops: publish notification realtime event (Redis and/or Kafka depending path).
8. Websocket broadcast: realtime-edge notification delivery to subscribed user sessions.
9. Response: asynchronous, no direct client REST response.
10. Errors: consumer catches/logging; behavior varies by consumer.

## H. Room Creation
1. Entry: `POST /api/v1/rooms`.
2. Validation: request DTO and membership assumptions.
3. Auth checks: creator from JWT.
4. Service calls: `roomService.createRoom`.
5. DB ops: room insert, membership insert.
6. Cache ops: potential room list cache refresh/invalidation.
7. Broker ops: potential member/system events depending implementation path.
8. Websocket broadcast: room/member events to affected users.
9. Response: created room payload.
10. Errors: validation/ownership constraints.

## I. Media Upload
1. Entry: `/api/v1/uploads/prepare` then `/confirm`.
2. Validation: upload purpose/file metadata.
3. Auth checks: JWT principal via security context.
4. Service calls: signing service prepare/confirm.
5. DB ops: confirm may persist metadata reference (service-specific model).
6. Cache ops: none primary.
7. Broker ops: generally none for core upload.
8. Websocket broadcast: none direct.
9. Response: signed params then finalized asset metadata.
10. Errors: invalid token, invalid purpose, external provider errors.

## J. Event Broadcast (Cross-Service)
1. Entry: domain event publish after transaction commit.
2. Validation: payload factory + envelope metadata generation.
3. Auth checks: internal trust path.
4. Service calls: producer abstraction to Kafka/Redis.
5. DB ops: source transaction already committed.
6. Cache ops: possible invalidation side effects in consumers.
7. Broker ops: event on topic/channel.
8. Websocket broadcast: realtime-edge delivery services fan to sessions.
9. Response: eventual client state convergence.
10. Errors: producer/consumer logs and retry behavior service-specific.
