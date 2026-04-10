## Why

The profile settings and profile overlays are currently only partially aligned, so users see different visual language and interaction behavior depending on where they click an avatar, name, or mention. A unified Discord-style profile system is needed now to make profile identity feel consistent across the app and to make self-vs-other click behavior predictable.

## What Changes

- Redesign profile settings to a more Discord-like profile customization surface while preserving existing account/security functionality.
- Introduce a unified profile presentation contract so avatar, icon, display name, and mention clicks render the same profile card structure across chat, lists, and popups.
- Define explicit click behavior split:
  - self profile click shows a direct action to open profile settings
  - other-user click shows a mini profile chat input (text and emoji/icon capable), and pressing Enter jumps into the full chat room.
- Reuse existing private-chat and chat-store flow for room activation and navigation instead of adding new backend APIs.

## Capabilities

### New Capabilities
- `profile-identity-presentation-consistency`: Ensures profile surface rendering is visually and behaviorally consistent for avatar/name/mention interactions across the frontend.
- `profile-overlay-self-vs-other-actions`: Defines deterministic self vs other profile actions, including settings entry for self and mini-chat-to-full-chat transition for other users.

### Modified Capabilities
- `discord-ui-features`: Extend modern styling requirements so profile settings and profile overlays follow the same Discord-inspired design language used by high-traffic social surfaces.

## Impact

- Affected code: `src/pages/ProfileSettingsPage.tsx`, `src/components/user/UserPopup.tsx`, profile renderer helpers (`resolveProfilePresentation` usage), avatar/username/mention entry points, and related tests.
- Affected stores/services: `auth.store`, `chat.store`, `userOverlay.store`, and existing private chat room creation flow (`startPrivateChatApi`).
- APIs: no backend contract changes expected; reuses existing room creation and message send endpoints.
- UX scope: profile settings page, profile popup/card consistency in chat and user surfaces, and self/other interaction outcomes.
