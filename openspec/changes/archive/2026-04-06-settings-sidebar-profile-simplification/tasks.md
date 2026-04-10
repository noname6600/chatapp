## 1. Settings Layout Simplification

- [x] 1.1 Remove Account Recovery tab from Settings section switcher and remove related tab-state branches
- [x] 1.2 Keep Profile and Security sections functional after tab removal (no dead routes/actions)
- [x] 1.3 Update Settings section default/active-state logic to avoid references to removed recovery tab

## 2. Profile Edit Visibility and About-Me Summary

- [x] 2.1 Remove conditional hide logic for Edit Profile action so it is always visible for authenticated self-settings
- [x] 2.2 Ensure Profile summary card always renders about-me value with deterministic fallback text when empty
- [x] 2.3 Verify profile edit workflow still updates displayName/username/aboutMe correctly after visibility changes

## 3. Profile Preview Parity Across Entry Points

- [x] 3.1 Create or extend shared profile presentation resolver for avatar/displayName/username-aboutMe/background fallback mapping
- [x] 3.2 Apply shared resolver in Settings preview components
- [x] 3.3 Apply shared resolver in chat avatar/name-triggered profile view surfaces
- [x] 3.4 Ensure invalid/missing profile metadata produces identical fallback output across settings and chat entrypoints

## 4. Navigation and UX Integration

- [x] 4.1 Confirm sidebar profile entrypoints continue routing to settings/profile flow after simplification
- [x] 4.2 Add or preserve clear recovery action placement in login/security surfaces (without Settings recovery tab)
- [x] 4.3 Remove obsolete UI labels/copy referencing Account Recovery tab in Settings

## 5. Validation and Regression Coverage

- [x] 5.1 Frontend test: Settings tabs render Profile and Security only (no Account Recovery)
- [x] 5.2 Frontend test: Edit Profile action is visible by default in Settings profile section
- [x] 5.3 Frontend test: about-me summary displays value or fallback deterministically
- [x] 5.4 Frontend test: avatar/name profile entrypoints in chat match settings preview identity mapping/fallbacks
- [x] 5.5 Manual verification: open profile from sidebar, chat avatar, and chat name and confirm parity of avatar/name/about-me/background
