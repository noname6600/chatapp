## Why

Users need the ability to review and edit message text before sending to catch errors or refine content before commit. Additionally, the message editing feature (edit after send) is currently broken or unavailable, preventing corrections to messages that have already been posted.

## What Changes

- Add a draft message review interface that displays composed message text with editable controls before send
- Allow users to modify message content in the draft review state without closing the composer
- Fix and enable message edit functionality to allow users to modify existing sent messages
- Ensure edited messages are properly reflected in the UI with clear edit indicators/timestamps

## Capabilities

### New Capabilities
- `message-draft-review`: Provides draft message viewing and editing interface before send, with controls to confirm or discard changes

### Modified Capabilities
- `message-item-reply-delete-actions`: Extend to add edit action and full message editing flow for sent messages

## Impact

- Frontend: Message input/composer component, message item rendering, inline message editing UI
- Backend: Message update endpoints, edit history tracking (if applicable)
- UX: New modal/panel for draft review, edit affordance on message rows
