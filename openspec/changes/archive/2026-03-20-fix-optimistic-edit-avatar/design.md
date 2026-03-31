## Context

The message editing flow in the application uses optimistic updates to provide immediate UI feedback. When a user edits a message, the UI shows the change before the server confirms it. However, during the optimistic phase, the rendered message incorrectly displays the wrong avatar and user name (appears to show the current user's info instead of the original message author's info). This bug occurs in all chat types (one-on-one and groups) and persists until the server response is received, at which point the UI corrects itself.

The issue likely stems from how the optimistic message object is constructed—the code may be either:
1. Taking user metadata from the current user context instead of the original message
2. Not preserving the original message's author field correctly when creating the optimistic state

## Goals / Non-Goals

**Goals:**
- Fix optimistic update state to correctly preserve the original message author's metadata (avatar, user name, user ID)
- Ensure the edited message displays with correct author attribution from the moment the edit begins until the server response is received
- Maintain all other optimistic edit behavior (edited content shows immediately, submission state is updated)

**Non-Goals:**
- Change the message data model or API contract
- Modify the edit API endpoint or request/response structure
- Handle other message metadata errors (timestamps, thread info, etc.) - scope limited to avatar and user name

## Decisions

**Decision 1: Preserve author metadata when creating optimistic message object**
- **Choice**: When constructing the optimistic message state, explicitly copy the original message's author information (userId, userName, userAvatar) instead of deriving it from the current user context
- **Rationale**: The original message's author should never change, even during an edit. Author metadata is immutable; only content changes.
- **Implementation Location**: In the edit message action/reducer, ensure the optimistic message structure includes the entire original author object
- **Alternatives Considered**:
  - Fetch author info from user store (rejected: unnecessary complexity and potential cache issues)
  - Use current user context (rejected: this is the current bug)

**Decision 2: Separate content update from metadata in optimistic state**
- **Choice**: Structure optimistic message as: `{...originalMessage, content: newContent, isEditing: true}` rather than replacing the entire message object
- **Rationale**: Ensures all fields from the original message are preserved; only update what actually changed
- **Alternative Considered**: Selectively copy fields (rejected: error-prone if new fields are added later)

**Decision 3: Validate author metadata exists before rendering**
- **Choice**: Add a defensive check in the message rendering component to fall back to message author metadata if current user context is unexpectedly used
- **Rationale**: Provides a safeguard against similar bugs in the future
- **Implementation**: In MessageItem component, prioritize `message.author` over any user context assumptions

## Risks / Trade-offs

**[Risk] Race condition if author field is missing in optimistic message** → Mitigation: Add fallback logic to detect missing author and log error; include type checking in TypeScript

**[Risk] Consistency between optimistic and server response author data** → Mitigation: Ensure the server response includes the canonical author metadata; validate it matches optimistic state

**[Risk] Group chat complexity where multiple users were mentioned in edit** → Mitigation: Edit scope is only the main message author, not mentions; confirmed in proposal

## Migration Plan

1. **No data migration needed**: This is a pure state management fix
2. **Deployment**: Update frontend code, redeploy chat bundle
3. **Rollback**: Revert to previous message editing logic
4. **Note**: No breaking changes or API modifications

## Open Questions

- Is the message object type definition (TypeScript interface) correctly including author metadata? Should we add strict typing to prevent this bug?
- Are there any other metadata fields (beyond avatar and name) that should be preserved during optimistic edits?
- Should we add telemetry or logging to detect if this bug occurs after the fix is deployed?
