## Context

The mention flow spans composer, user cache fetch, and message-list rendering. Failures in any layer cause visible regressions:
- Missing username in bulk profile responses degrades mention suggestion identity and insertion token quality.
- Mention text can be sent but displayed as raw token instead of canonical `@DisplayName`.
- Highlight behavior currently does not consistently distinguish self mention vs other mention.
- Mention tokens are not consistently interactive for profile popup in all render paths.

## Goals / Non-Goals

**Goals:**
- Provide deterministic mention insertion output that never inserts undefined values and always remains sendable.
- Preserve bounded suggestions (max 5) and dual-field search (display name + username).
- Ensure bulk profile data provides stable username for suggestion right-column rendering.
- Render all mentions in chat as `@DisplayName` regardless of typed alias/token.
- Make mention tokens clickable to open user profile popup from message list.
- Apply highlight policy:
	- self mentioned => full message item highlight
	- other mention => token-only highlight
- Preserve keyboard and click selection support.

**Non-Goals:**
- Replacing the existing popup component with a new profile surface.
- Changing mention storage protocol to a new backend parsing format.
- Reworking unrelated message rendering features outside mention behavior.

## Decisions

1. Canonical mention metadata across layers
- Decision: Mention selection stores canonical identity (`userId`, `username`, `displayName`) and mention extraction references this canonical map for send payload.
- Rationale: Prevents undefined insertion and ensures send payload consistency even when display label differs from typed token.
- Alternative considered: Serialize only inserted token text. Rejected due to ambiguity and stale/alias mismatch.

2. Candidate cap and dual-field ranking
- Decision: Build search index from display name + username and normalize case/diacritics consistently.
- Decision: Render only the top 5 ranked results per query.
- Rationale: Users remember either field; matching both improves discoverability while cap reduces noisy lists.
- Alternative considered: Full unbounded list. Rejected because it increases ambiguity and accidental selection.

3. Display rendering normalization
- Decision: Message renderers (plain + block) resolve mention tokens against user cache and display as `@DisplayName`.
- Rationale: Keeps chat UI human-readable and consistent independent of typed alias.
- Alternative considered: Render raw token as sent. Rejected because aliases and username changes reduce readability.

4. Highlight semantics by target ownership
- Decision: Compute mention target ownership at message-item level:
	- self-mentioned => apply full-row highlight class
	- otherwise => apply token-level highlight only on mention span
- Rationale: Mirrors expected urgency while keeping non-self mentions visually lightweight.
- Alternative considered: Always row-level highlight. Rejected for high visual noise.

5. Clickable mentions and profile popup integration
- Decision: Mention spans become interactive controls that open existing user profile popup by resolved mentioned user id.
- Rationale: Provides fast profile context without introducing a new UI surface.
- Alternative considered: Keep mentions non-interactive. Rejected against requirement.

6. Traceability logs for username propagation
- Decision: Add temporary logs at frontend fetchUsers/getUsersBulkApi, mention suggestion mapping, and backend bulk endpoint/service mapping.
- Rationale: Rapidly identifies whether username loss occurs in DB mapping, HTTP response, store cache, or suggestion mapping.
- Alternative considered: Debugging only with breakpoints. Rejected for slower team-wide reproducibility.

## Risks / Trade-offs

- [Risk] Display-name rendering may fail when user cache misses mentioned target → Mitigation: use graceful fallback label and trigger lazy user fetch where possible.
- [Risk] Clickable mention spans could conflict with text selection UX → Mitigation: use lightweight button/link semantics and preserve standard selection behavior in non-click zones.
- [Risk] Row highlight policy may regress existing reply-linked highlight logic → Mitigation: merge class conditions with deterministic precedence and add tests for combined states.
- [Risk] Temporary logs can be noisy in local environments → Mitigation: remove/debug-gate logs after issue is resolved and verified.
