## ADDED Requirements

### Requirement: Feature module folder structure
Each feature SHALL have a standardized folder structure at `/src/features/<feature-name>/` containing organized, co-located code for that feature. The structure allows a developer to understand and modify a complete feature without navigating across multiple directories.

#### Scenario: Feature module organization
- **WHEN** navigating to `/src/features/<feature-name>/`
- **THEN** the folder contains: `components/`, `hooks/`, `store/`, `types/`, `api/`, optionally `websocket/`, and `index.ts`

#### Scenario: Core features migration
- **WHEN** refactoring existing features (auth, chats, rooms, friends, presence, profile, notifications)
- **THEN** each feature SHALL have its code organized within its feature folder following the standard structure

#### Scenario: Index exports pattern
- **WHEN** importing from a feature module
- **THEN** `index.ts` SHALL export all public APIs (components, hooks, store, types) from the feature for simplified imports

### Requirement: Feature modules must not depend on each other
Feature modules SHALL only depend on `/src/core` (global concerns) and shared utilities `/src/components/shared` and `/src/utils`. No feature SHALL directly import from another feature's implementation details.

#### Scenario: Cross-feature dependency prevention
- **WHEN** feature A needs functionality from feature B
- **THEN** feature B must expose the functionality via its `index.ts` exports OR the shared utilities / core
- **THEN** feature A imports only from feature B's public interface, never from internal files

#### Scenario: Direct feature-to-feature import
- **WHEN** code in `features/chats/components/ChatBox.tsx` attempts to import from `features/rooms/store/rooms.store.tsx`
- **THEN** TypeScript linting or ESLint SHALL flag this as a violation