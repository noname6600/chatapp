## Context

Currently, edited messages show the "edited at" timestamp in a fixed location (new line) regardless of edit scope. The message block rendering doesn't distinguish between single-line text edits and complex multi-block edits, resulting in inconsistent UX. Empty blocks created during editing add unwanted spacing. Additionally, @mentions in edited text blocks fall back to plain text rendering instead of using the mention resolution system available in normal composition, creating inconsistency.

**Current State:**
- All edited messages show "edited at" label below the entire message content
- Message block structure is preserved but spacing isn't optimized
- Edit text input doesn't support mention detection/resolution
- MessageContent component renders all blocks identically regardless of edit history

**Constraints:**
- Must maintain backward compatibility with existing messages
- Edit API already supports blocks; no backend changes needed
- Mention resolution uses existing `useMention` hook and `resolveMentionLabel`/`resolveMentionUserId` utilities
- Timestamp formatting uses existing `formatMessageTimeShort` function

## Goals / Non-Goals

**Goals:**
1. Display "edited at" timestamp inline for single-text-block edits (no line break)
2. Display "edited at" timestamp on new line for multi-block edits
3. Eliminate spacing artifacts from empty blocks to keep UI clean
4. Enable @mention support in edited text blocks with proper user resolution
5. Maintain consistency between mention behavior in edits and normal composition

**Non-Goals:**
- Track per-block edit history (not storing which block was edited when)
- Modify backend edit API or storage
- Change timestamp format or timezone handling
- Add visual editing markers (e.g., "block X was edited")

## Decisions

### Decision 1: Timestamp Placement Logic
**Choice:** Determine placement in `MessageContent` based on block count, not message type

**Rationale:**
- Single TEXT block with inline timestamp is cleaner visually
- Multiple blocks (even if all TEXT) benefit from line-separated timestamp for clarity
- Logic is simple: `blocks.length === 1 ? inline : newline`
- Works for MIXED messages and pure TEXT messages consistently

**Alternatives Considered:**
1. Always use newline (rejected: wastes space for single-line edits)
2. Use message type (TEXT vs MIXED) (rejected: doesn't handle multi-block TEXT messages)
3. Add explicit `editStyle` metadata to message (rejected: requires backend change)

### Decision 2: Empty Block Handling
**Choice:** Filter out blocks with no text content when rendering in MessageBlocks component

**Rationale:**
- Empty blocks occur when users add a block but don't fill it before saving
- Filtering at render time (not storage time) maintains data integrity without backend change
- Keeps UI clean without modifying stored message structure

**Alternatives Considered:**
1. Filter on backend (rejected: API already extended, not the root cause)
2. Skip rendering empty blocks in BlockMessageEditor during edit (rejected: user should see what they're saving)
3. Prevent empty block submission (rejected: adds validation complexity)

### Decision 3: Mention Support in Edits
**Choice:** Reuse `useMention` hook and resolution utilities in `InlineEditInput` to match composer behavior

**Rationale:**
- User expectations: mentions should work the same way everywhere
- Code reuse: mention detection, filtering, and rendering already exist
- Consistent UX: same autocomplete, same mention styling
- Minimal changes: hook already accepts dynamic content

**Alternatives Considered:**
1. Strip mentions on edit (rejected: reduces feature completeness)
2. Create separate mention handler for edits (rejected: code duplication)
3. Fallback to plain text mentions (rejected: inconsistent with normal composition)

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **Single vs Multi-block detection logic fails** | Timestamp appears in wrong location | Add unit tests for edge cases (0 blocks, 1 block, many blocks); test with real message data |
| **Filtering empty blocks breaks rendering** | Message displays incorrectly | Render filter carefully; keep storage intact; test message load with old edits |
| **Mention resolution slower in edit mode** | Edit feel sluggish | Mention hook already optimized; should be imperceptible; monitor performance |
| **User confusion: mentions work differently in edits** | This is the opposite risk—addressed by design choice | Consistent implementation reduces confusion |

**Trade-offs:**
- **Simplicity vs Generality**: Logic is simple (blocks.length check) but assumes all blocks should be treated equally. Mitigation: could extend later if per-block edit tracking is needed.
- **Storage vs Rendering**: Empty blocks stored but not rendered. Mitigation: acceptable because we don't modify message on backend; UI handles it.

## Migration Plan

**Implementation Path:**
1. Create timestamp inline/newline logic in MessageContent
2. Update MessageBlocks to filter empty blocks
3. Add mention support to InlineEditInput
4. Test with existing edited messages (should still display correctly)
5. Deploy progressively; no database migration needed

**Rollback Strategy:**
- All changes are UI-only; no data storage changes
- Revert commits will restore previous timestamp/mention behavior
- No feature flags needed (behavior is backward-compatible)

## Open Questions

1. **Should edited-at timestamp be collapsible/hideable for verbose messages?** → Deferred; can add UI polish later
2. **Should we track which block was edited for future features?** → No; deferred to future work if needed
3. **Should empty blocks be permanently filtered from DB or just UI?** → UI-only filtering; maintain storage integrity
