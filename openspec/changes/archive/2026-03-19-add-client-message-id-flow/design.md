# Design: Add Client Message ID Flow

## Context

The `ChatMessage` entity already has a `clientMessageId` field (`@Column(length = 100)`) with a database
index (`idx_msg_client_id`). The field was introduced to support idempotent message delivery and optimistic
UI reconciliation on the frontend, but it was never wired through the application stack. No DTO, pipeline
step, WebSocket model, Kafka payload, or repository query currently uses it.

The send pipeline has 9 ordered steps:
`ExtractMentionStep → MapAttachmentDraftStep → GenerateSequenceStep → CreateMessageAggregateStep →
ValidateMessageStep → ValidateRoomPermissionStep → PersistMessageStep → PersistMentionStep → PublishMessageEventStep`

Schema is managed by Hibernate `ddl-auto: update` (no Flyway/Liquibase migrations needed — the column already exists in the entity).

## Goals / Non-Goals

**Goals:**
- Accept `clientMessageId` from clients via HTTP REST `POST /api/v1/messages` and WebSocket `SEND` command
- Persist it through the `MessageAggregate` → `ChatMessage` entity
- Echo it back in `MessageResponse` and include it in `ChatMessagePayload` Kafka events
- Provide idempotency: if a message with the same `(roomId, clientMessageId)` pair already exists, return the existing message instead of creating a duplicate

**Non-Goals:**
- Cross-service deduplication (e.g., notification-service does not need to deduplicate on this field)
- Enforcing globally unique `clientMessageId` across all rooms — uniqueness is scoped to `(roomId, clientMessageId)`
- Migrating historical messages (existing messages simply have `null` clientMessageId)
- Frontend implementation changes (this change covers the backend contract only)

## Decisions

### 1. Idempotency Check Location: Service Layer (before pipeline)

**Decision**: Perform the duplicate check in `MessageCommandService.sendMessage()` before invoking the
send pipeline, not as a pipeline step.

**Rationale**: A mid-pipeline short-circuit would require adding conditional branching logic to both the
step interface and pipeline executor — adding complexity that isn't justified for a single feature. Checking
at the service entry point is clean, readable, and consistent with how most idempotency guards are placed.

**Alternative considered**: Adding a `CheckClientMessageIdempotencyStep` as the first pipeline step.
Rejected because the existing `PipelineExecutor` runs all steps in order without early-exit support.

### 2. Repository Query: JPQL findByRoomIdAndClientMessageId

**Decision**: Add `Optional<ChatMessage> findByRoomIdAndClientMessageId(UUID roomId, String clientMessageId)`
as a simple derived query (Spring Data naming convention) on `ChatMessageRepository`.

**Rationale**: The `idx_msg_client_id` index already exists and covers this lookup efficiently. A derived
query requires no custom JPQL and is backed by that index.

### 3. Pass clientMessageId Through MessageAggregate.create()

**Decision**: Add `clientMessageId` as an optional parameter to `MessageAggregate.create()` and set it on
the constructed `ChatMessage`.

**Rationale**: `MessageAggregate.create()` is the single factory method for new messages. Centralising the
field assignment there keeps the aggregate consistent regardless of call path.

### 4. clientMessageId is Optional on All Inputs

**Decision**: `clientMessageId` has no `@NotNull` or `@NotBlank` constraint on `SendMessageRequest` or
`WsIncomingMessage`. When absent (`null`), the idempotency check is skipped.

**Rationale**: Clients that do not yet produce a `clientMessageId` must continue to work without changes.
Backward compatibility is a hard requirement.

### 5. ChatMessagePayload in common-events: Add Field with JsonCreator Update

**Decision**: Add `clientMessageId` to `ChatMessagePayload` with a new `@JsonProperty` in the `@JsonCreator`
constructor. Because `@JsonIgnoreProperties(ignoreUnknown = true)` is already present, downstream consumers
that do not consume this field are unaffected.

**Rationale**: Keeping the Kafka payload in sync with the entity prevents inconsistencies for consumers
(e.g., notification-service) that may need to correlate events in the future.

## Risks / Trade-offs

- **Race condition on idempotency check**: Two concurrent requests with the same `clientMessageId` for the
  same room could both pass the existence check before either is persisted (TOCTOU). Mitigation: the
  database index `idx_msg_client_id` is not unique, so no hard guarantee; however, the window is narrow and
  duplicate sends are a recoverable edge case for the chat use case (not financial). A unique partial index
  could be added later if stronger guarantees are needed.

- **clientMessageId max length 100**: Enforced by the entity column definition. `SendMessageRequest` should
  add a `@Size(max = 100)` validation to fail fast at the API boundary rather than at the DB layer.

- **null in Kafka payload**: `clientMessageId` will be `null` for all messages created before this change
  and for clients that do not provide it. Downstream Kafka consumers must handle `null` gracefully.
  `@JsonIgnoreProperties(ignoreUnknown = true)` on the payload ensures deserialization remains backward
  compatible in both directions.

## Migration Plan

1. Add field to `common-events` `ChatMessagePayload` first (backward-compatible addition)
2. Update chat-service: DTO → pipeline → mapper → repository → WebSocket
3. Deploy chat-service (entity already has the column; `ddl-auto: update` handles no-op)
4. No rollback steps needed — all changes are additive

## Open Questions

- Should `clientMessageId` be validated as a specific format (e.g., UUID string)? Currently any string ≤ 100
  chars is accepted. Decided: leave unforced for now to give clients flexibility.
