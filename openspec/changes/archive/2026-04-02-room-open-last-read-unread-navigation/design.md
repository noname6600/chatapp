## Context

Unread behavior is currently split across room-store counters, message-list pagination, and in-view indicators. Users entering active rooms need deterministic positioning at the last-read boundary, while realtime updates continue to append newer messages. The design must coordinate boundary calculation, viewport anchoring, bidirectional loading, and mark-read triggers without introducing duplicate requests or UI thrash.

## Goals / Non-Goals

**Goals:**
- Enter each room at a stable last-read anchor when unread exists.
- Render an unread divider only when boundary is present in loaded messages.
- Support scrolling both directions from the anchor: older history upward and newer messages downward.
- Show top affordances when user is away from latest: distance-to-latest jump and incremental new-message notifications.
- Keep unread counts and mark-read state convergent with backend truth.
- Make mark-read triggers explicit, idempotent, and testable.

**Non-Goals:**
- Replacing backend unread model or read-sequence storage semantics.
- Introducing entirely new transport protocols beyond existing HTTP/WebSocket flows.
- Redesigning full chat layout or message rendering theme.

## Decisions

### 1. Use read-sequence anchor as room-entry source of truth
- Decision: derive entry boundary from backend unread/read metadata (for example `lastReadSeq` + room latest seq) and compute first unread seq.
- Rationale: backend is authoritative across refresh and multi-device use; client-only anchors drift.
- Alternative considered: persist last scroll offset locally per room. Rejected because it does not align with unread semantics and can be stale after remote activity.

### 2. Keep boundary and entry orchestration in list-level layer
- Decision: compute boundary index, anchor scroll target, and divider placement in message-list orchestration layer, not item-level components.
- Rationale: boundary logic requires full-window context and pagination state.
- Alternative considered: item-level self-detection of unread state. Rejected due to list-wide coupling and inconsistent rendering.

### 3. Introduce dual directional pagination state around active window
- Decision: maintain both `oldestSeq` and `newestSeq` markers for the loaded window so scrolling up loads older pages and scrolling down can fetch missing newer pages when not at live tail.
- Rationale: user flow includes reading old context and then returning to newer unread messages without forcing jump.
- Alternative considered: only support upward pagination plus explicit jump. Rejected because it blocks smooth downward continuation from historical contexts.

### 4. Top indicator model separates two intents
- Decision: use two related but distinct states while user is away from live tail:
  - `unseenIncomingCount` for newly arrived messages after current viewport point (e.g., "1 new message").
  - `distanceToLatest` indicator/jump affordance when latest loaded/known point is far away (e.g., ~100 messages).
- Rationale: one state signals realtime arrivals, the other signals current positional distance.
- Alternative considered: a single generic banner. Rejected because users cannot distinguish backlog distance from new arrivals.

### 5. Mark-read policy is interaction-based and idempotent
- Decision: mark read when one of the defined interactions occurs:
  - viewport crosses unread boundary into unread region,
  - user executes jump-to-latest,
  - user reaches live tail and remains in focus state.
  Guard with per-room in-flight and per-session dedupe to avoid duplicate calls.
- Rationale: prevents premature read acknowledgements while avoiding read-state lag.
- Alternative considered: mark read immediately on room open. Rejected because it can clear unread before user views content.

### 6. Reconciliation loop always favors backend snapshot
- Decision: after reconnect/refresh and after mark-read success, refresh room snapshot and reconcile local unread counters/indicators by replacement, not additive merge.
- Rationale: avoids divergence from websocket timing races.
- Alternative considered: purely event-driven local math. Rejected due to race susceptibility.

## Risks / Trade-offs

- [Boundary not in loaded window] -> Might show no divider and confuse users -> Mitigation: explicit top indicator text explaining unread exists outside current window and offer jump/load action.
- [Realtime bursts while paginating] -> Indicator counts may flicker or over-increment -> Mitigation: event coalescing and monotonic counter updates tied to message ids/sequences.
- [Duplicate mark-read requests] -> Extra backend load and racey UI state -> Mitigation: per-room in-flight guard and idempotent backend endpoint usage.
- [Bidirectional pagination complexity] -> More edge cases in scroll restoration -> Mitigation: bounded window strategy plus deterministic anchor restoration tests.

## Migration Plan

1. Add list-level boundary/anchor computation behind a feature flag or guarded path.
2. Add top indicators and dual-direction pagination state without removing current jump behavior.
3. Enable interaction-based mark-read triggers with dedupe guards.
4. Validate with unit/integration + manual multi-user realtime scenarios.
5. Remove obsolete fallback logic once metrics and QA confirm stable convergence.

Rollback:
- Disable new entry-anchor and mark-read trigger paths, revert to existing room-open/jump behavior while retaining non-breaking UI components.

## Open Questions

- Should distance indicator show approximate count (e.g., "99+") or exact gap sequence count?
- What debounce threshold should be used before marking read on boundary crossing during rapid scroll?
- Should arriving-new-message indicator auto-collapse after short inactivity or remain sticky until user action?
- Do we need backend endpoint support to fetch "messages after seq" if current client only has "before/around" queries?
