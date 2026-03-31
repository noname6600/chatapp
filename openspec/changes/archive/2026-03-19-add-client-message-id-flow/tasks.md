## 1. common-events: ChatMessagePayload

- [x] 1.1 Add `clientMessageId` (`String`) field to `ChatMessagePayload` in common-events
- [x] 1.2 Add `@JsonProperty("clientMessageId")` parameter to `@JsonCreator` constructor in `ChatMessagePayload`
- [x] 1.3 Build common-events to verify it compiles cleanly

## 2. chat-service DTOs

- [x] 2.1 Add `@Size(max = 100)` validated `clientMessageId` field to `SendMessageRequest`
- [x] 2.2 Add `clientMessageId` field to `MessageResponse`

## 3. WebSocket Model

- [x] 3.1 Add `clientMessageId` field (`String`) to `WsIncomingMessage` with `@JsonProperty`
- [x] 3.2 Update `WsIncomingMessage` `@JsonCreator` constructor to accept `clientMessageId`

## 4. Domain Model: MessageAggregate

- [x] 4.1 Add `clientMessageId` parameter to `MessageAggregate.create()` factory method
- [x] 4.2 Set `clientMessageId` on the `ChatMessage` builder inside `MessageAggregate.create()`

## 5. Send Pipeline

- [x] 5.1 Add `clientMessageId` field to `SendMessageContext` (read from `SendMessageRequest`)
- [x] 5.2 Update `CreateMessageAggregateStep` to pass `request.getClientMessageId()` when calling `MessageAggregate.create()`

## 6. Repository

- [x] 6.1 Add `Optional<ChatMessage> findByRoomIdAndClientMessageId(UUID roomId, String clientMessageId)` to `ChatMessageRepository`

## 7. Idempotency Check in Service

- [x] 7.1 In `MessageCommandService.sendMessage()`, before invoking the pipeline: if `request.getClientMessageId()` is non-null, query the repository for an existing message with that `(roomId, clientMessageId)`
- [x] 7.2 If a duplicate is found, load its attachments and return a `MessageResponse` immediately (skip pipeline)

## 8. Response Mapper

- [x] 8.1 Add `.clientMessageId(message.getClientMessageId())` to the `MessageResponse.builder()` call in `MessageMapper.toResponse()`

## 9. Kafka Event Payload

- [x] 9.1 Add `.clientMessageId(message.getClientMessageId())` to the `ChatMessagePayload.builder()` call in `ChatMessagePayloadFactory.from()`

## 10. WebSocket Handler

- [x] 10.1 In `ChatWebSocketHandler`, pass `cmd.getClientMessageId()` when constructing `SendMessageRequest`

## 11. Verification

- [x] 11.1 Write or update unit test for `MessageCommandService` — verify duplicate `clientMessageId` short-circuits and returns existing message
- [x] 11.2 Write or update unit test for `CreateMessageAggregateStep` — verify `clientMessageId` is set on the aggregate
- [x] 11.3 Write or update unit test for `MessageMapper` — verify `clientMessageId` is mapped to response
- [x] 11.4 Build entire chat-service module and verify zero compilation errors
- [ ] 11.5 Smoke-test: send a message with `clientMessageId` via REST, verify it appears in the response; resend the same `clientMessageId` and verify the same message is returned (no duplicate created)
