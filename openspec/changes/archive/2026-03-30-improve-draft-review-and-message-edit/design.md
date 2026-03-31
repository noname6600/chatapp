## Context

The chat application currently allows users to compose messages in the input field, but provides no way to review or edit the draft before sending. Once a message is sent, users cannot edit it due to incomplete or unavailable message editing functionality. This creates friction for users who want to catch typos/refine wording before commit, and prevents recovery from sending unintended messages.

The message composer is currently built into the message input component. The message item row has action affordances (reply, delete) but lacks an edit action.

## Goals / Non-Goals

**Goals:**
- Allow users to view composed message text in a review/confirm state before final send
- Provide inline editing capability within the review state to modify message content
- Enable message editing for sent messages with clear edit indicators
- Maintain message send reliability (no breaking changes to send flow)
- Support both draft review flow (edit-before-send) and post-send editing independently

**Non-Goals:**
- Draft auto-save/persistence across sessions (save-to-disk for recovery left to future work)
- Version history of message edits (edit tracking is out of scope)
- Rich text editing (editing scope limited to plain text)
- Collaborative drafts (single-user draft focus only)

## Decisions

**Decision 1: Draft Review as Modal/Panel**
- **Choice**: Implement draft review as a modal or side panel that displays composed message
- **Rationale**: Separates review state from active input, provides clear intent-to-send boundary, reduces accidental sends
- **Alternatives Considered**: Inline expansion (less clear boundary), toast confirmation (insufficient space for editing)
- **Implementation**: Show modal on user action (e.g., button click or keyboard shortcut) that displays message text + edit/send/cancel buttons

**Decision 2: Edit-Before-Send vs Edit-After-Send as Parallel Flows**
- **Choice**: Implement draft review (edit-before-send) as separate feature from message edit (edit-after-send)
- **Rationale**: Different UX contexts (pre-send vs post-send), different backend concerns (no persistence for draft vs update for sent), easier to test and deploy independently
- **Alternatives Considered**: Merge both into single "message edit" flow (too broad, complicates send path)

**Decision 3: Message Edit as Inline Row Action**
- **Choice**: Add "Edit" action button on message item row (alongside Reply, Delete)
- **Rationale**: Consistent with existing message action pattern, no new interaction model required, familiar to users
- **Alternatives Considered**: Dedicated modal editor (more context switch), separate edit panel (adds complexity)
- **Implementation**: Edit action opens inline editor in the same message row with save/cancel controls

**Decision 4: Backend Message Update Endpoint**
- **Choice**: Use existing or create PUT/PATCH endpoint to update message content on server
- **Rationale**: Standard REST pattern, idempotent by design, allows audit/logging of edits
- **Alternatives Considered**: WebSocket EDIT command (mirrors existing SEND, but adds protocol complexity)

## Risks / Trade-offs

- **Risk**: Draft review modal adds step to send flow → could increase perceived latency if users feel it's redundant
  - *Mitigation*: Make review optional via user preference or keyboard shortcut, don't force all sends through modal
  
- **Risk**: Message editing after send creates potential for confusing conversation context if users edit heavily
  - *Mitigation*: Show "Edited at HH:MM" timestamp on edited messages, design edit affordance subtly so it's discoverable but not over-prominent

- **Risk**: Stale UI state if another user edits a message while viewer is looking at it
  - *Mitigation*: Refresh message from server after any edit action, add visual indicator if message was edited by self

- **Risk**: Users might attempt to edit deleted messages or run into race conditions with delete-before-edit
  - *Mitigation*: Disable edit action for deleted messages in UI, handle 404/conflict responses from server gracefully

## Open Questions

- Should draft review be opt-in (user can enable/disable) or always-available (opt-out)?
- Should edit timestamp be shown inline on message or in a tooltip/details view?
- Should there be a limit on how long after sending a message can still be edited (e.g., 5 min edit window)?
- Should message edit be broadcast to other users in real-time or only visible after refresh?
