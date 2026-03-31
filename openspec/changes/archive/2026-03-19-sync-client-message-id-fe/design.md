# Design: Sync clientMessageId to Frontend

## Context

The backend (`add-client-message-id-flow`) now accepts a `clientMessageId` string on every message send
(HTTP and WebSocket), persists it on the entity, echoes it back in the response, and enforces idempotency
at `(roomId, clientMessageId)` scope.

The frontend's current optimistic reconciliation matches server-confirmed messages to optimistic placeholders
using a triple of `(senderId, content, replyToMessageId)`. This is fragile: two consecutive identical
messages from the same user will replace the wrong placeholder, and a user who double-taps send creates a
duplicate that is never reconciled.

Two send paths coexist in the current frontend:
1. **Primary** — `MessageInput.send()` → `sendMessageSocket()` → WebSocket `SEND` command
2. **Secondary** — `store.sendMessage()` → `sendMessageApi()` → HTTP POST

Both paths must be updated.

## Goals / Non-Goals

**Goals:**
- Generate a stable UUID `clientMessageId` per outgoing message on the frontend
- Attach `clientMessageId` to both the WebSocket SEND command and the HTTP payload
- Carry `clientMessageId` on optimistic messages so reconciliation can use it
- Replace content-based reconciliation with `clientMessageId`-based matching
- Fall back to content-based matching for messages that have no `clientMessageId` (legacy / received-only messages)

**Non-Goals:**
- Persisting `clientMessageId` across page reloads (in-memory per session is sufficient)
- Deduplicating the send action in the UI (double-click prevention is a separate concern)
- Changes to the backend (already done)
- FE retry / resend logic using `clientMessageId` (future enhancement)

## Decisions

### 1. Generate clientMessageId in MessageInput (primary path) and store.sendMessage (secondary path)

**Decision**: Generate `crypto.randomUUID()` at the call site of the optimistic message creation in both
`MessageInput.send()` and `store.sendMessage()`. The same UUID is used for both the optimistic placeholder
and the actual send payload.

**Rationale**: The ID must be available at the moment the optimistic message is built so the store can store
it. Generating at the call site keeps generation co-located with usage, avoiding threading a generated value
through multiple layers.

**Alternative**: Generate inside `sendMessageSocket()`. Rejected — the optimistic message is created before
`sendMessageSocket()` is called, so the ID wouldn't be available for the placeholder.

### 2. Store clientMessageId on the ChatMessage type as optional

**Decision**: Add `clientMessageId?: string | null` to the `ChatMessage` interface. It is optional and
nullable to remain backward-compatible with messages fetched from history (which have `null` from the server
for messages sent before this feature) and live messages from other users (which may not have been sent with
a `clientMessageId`).

### 3. Reconciliation: clientMessageId-first, content-based fallback

**Decision**: In the store's upsert/reconcile logic:
1. If the incoming server message has a `clientMessageId`, find the optimistic placeholder by matching
   `clientMessageId`
2. Only if no match found (or `clientMessageId` is null), fall back to the existing
   `(senderId, content, replyToMessageId)` content-based match

**Rationale**: Backward compatibility — messages sent before this feature, and messages received from other
clients that haven't updated yet, will have no `clientMessageId`. The fallback ensures those still reconcile.

**Alternative**: Remove the fallback entirely and require `clientMessageId` on all sends. Rejected — too
risky during rollout; other clients may lag.

### 4. sendMessageSocket signature change: add clientMessageId parameter

**Decision**: Add an optional `clientMessageId?: string` parameter to `sendMessageSocket()`. Existing
callers that don't pass it continue to work (UUID simply not included in the WS payload).

**Rationale**: Minimal interface change, easy to grep and update all callers.

## Risks / Trade-offs

- **UUID collision**: `crypto.randomUUID()` generates v4 UUIDs; collision probability is astronomically low
  for in-session message volumes. No mitigation needed.

- **MessageInput vs store path inconsistency**: If a caller uses `store.sendMessage()` without passing a
  `clientMessageId`, the store generates one internally and that's consistent. If a caller uses
  `sendMessageSocket()` directly without a `clientMessageId`, the WS payload won't have the field and no
  idempotency protection applies. Mitigation: document the expectation; make `clientMessageId` required in
  `MessageInput.send()`.

- **Optimistic message that never reconciles**: If the WS connection drops after the optimistic message is
  shown but before the server-confirmed message arrives, the placeholder stays indefinitely. This is a
  pre-existing issue unrelated to `clientMessageId`. No change in behavior.

## Migration Plan

1. Update types (`ChatMessage`, `SendMessagePayload`)
2. Update WebSocket helper (`sendMessageSocket`)
3. Update store reconciliation logic
4. Update `MessageInput` (primary send path)
5. Update `store.sendMessage()` (secondary HTTP path)
6. No backend changes needed — already deployed

## Open Questions

- Should `clientMessageId` be shown in the UI for debugging? Decided: no — internal field only.
