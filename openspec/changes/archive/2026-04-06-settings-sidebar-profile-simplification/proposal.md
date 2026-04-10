## Why

The current Settings structure exposes an extra Account Recovery tab and keeps edit-profile visibility conditional, which adds friction for users trying to manage profile data quickly. Profile preview consistency is also incomplete because avatar/name entrypoints in chat can render profile identity differently from the settings preview.

## What Changes

- Remove the Account Recovery section/tab from the Settings page shell.
- Keep profile management inside Settings as the primary account-edit surface.
- Ensure Edit Profile controls are always visible (remove hidden/conditional gate that prevents editing visibility).
- Add and consistently show `aboutMe` in settings profile summary/preview.
- Normalize profile identity preview rendering so display result (avatar, display name, username/about me, background usage) matches when opening profile from avatar/name click surfaces in chat.
- Align sidebar/profile interactions with this simplified settings-first model, avoiding redundant tab switching for profile editing flows.

## Capabilities

### New Capabilities
- `settings-profile-simplified-layout`: Simplified settings account UI with profile-focused sections and removed recovery tab.
- `settings-profile-edit-visibility`: Always-visible edit-profile controls and about-me presentation in settings summary/preview.
- `profile-preview-parity`: Consistent profile preview rendering across settings and chat avatar/name entrypoints.

### Modified Capabilities
- None.

## Impact

- Frontend pages/components: Settings page structure, profile summary card, profile editor visibility rules, and chat profile entry rendering.
- Frontend state/normalization: shared profile preview mapping for avatar/display name/username/about me/background.
- Minimal backend impact: no new APIs required unless preview payload mapping needs an additional field normalization check.
