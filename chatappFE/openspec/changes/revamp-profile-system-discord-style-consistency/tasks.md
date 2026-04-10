## 1. Discord-Style Profile Settings Redesign

- [x] 1.1 Redesign `src/pages/ProfileSettingsPage.tsx` to a Discord-like profile customization layout while preserving current profile and security flows.
- [x] 1.2 Update supporting profile components (`src/components/profile/ProfileEditor.tsx`, `src/components/profile/ProfilePreview.tsx`) to match the new visual system and hierarchy.
- [x] 1.3 Ensure profile draft changes (avatar/background/displayName/username/about) remain functionally intact with the redesigned settings UI.

## 2. System-Wide Profile Surface Consistency

- [x] 2.1 Define and apply a shared profile-card presentation contract for avatar/name/mention entry points, reusing `resolveProfilePresentation` and consistent card structure.
- [x] 2.2 Update profile-trigger components (`src/components/user/UserAvatar.tsx`, `src/components/user/Username.tsx`, mention-capable surfaces) to open the same profile card experience.
- [x] 2.3 Ensure edited profile attributes propagate consistently across settings preview and all profile card entry points.

## 3. Self vs Other Overlay Behavior

- [x] 3.1 Update `src/components/user/UserPopup.tsx` so self profile shows a clear action button routing to profile settings.
- [x] 3.2 Keep other-user profile popup focused on mini chat input that supports text and emoji/icon characters.
- [x] 3.3 Implement Enter-key behavior in mini chat to create/resolve private room via existing flow, activate room, and navigate to `/chat`.
- [x] 3.4 Ensure secondary actions (friend/remove/block/more) do not break primary self/other interaction behavior.

## 4. Verification and Regression Coverage

- [x] 4.1 Add/update tests for profile settings redesign and profile-card visual consistency expectations.
- [x] 4.2 Add/update tests for `UserPopup` self-button routing and other-user Enter-to-chat transition behavior.
- [x] 4.3 Run targeted frontend tests for profile settings, profile overlay interactions, and popup routing/chat transitions.
