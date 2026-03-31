## Why

Reply preview click-to-jump is unreliable when the replied-to message is outside the current window. In these cases, users cannot navigate to referenced context, and failure states are unclear when the original message was deleted.

## What Changes

- Strengthen reply jump resolution so clicking a reply can recover target context when the target is not currently loaded.
- Add bounded historical backfill attempts before failing jump resolution, instead of a single around-query attempt.
- Add explicit deleted/unavailable fallback behavior so UI communicates why jump cannot complete.
- Ensure deleted-target clicks do not perform a jump; viewport remains where user currently is.
- After a successful jump to an unloaded target, keep pagination continuity so users can scroll up for older and down for newer from that anchor context.
- Preserve stable viewport behavior during jump loading (no random top snap, no broken scroll state).

## Capabilities

### New Capabilities
- `reply-jump-fallback-resolution`: Bounded multi-step fallback path for reply jump when original target is outside loaded window or no longer retrievable.

### Modified Capabilities
- `message-reply-preview-resilience`: Extend click-to-jump requirement to cover unloaded-history fallback and deleted-target terminal state.

## Impact

- Frontend chat timeline behavior in reply preview click handling.
- Chat store paging interactions while resolving reply jump context.
- Message-level fallback text/state for deleted or unavailable originals.
- Unit/integration tests for jump resolution across unloaded and deleted-target cases.
