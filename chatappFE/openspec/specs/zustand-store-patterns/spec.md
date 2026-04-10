## ADDED Requirements

### Requirement: Feature store structure and organization
Each feature that manages state SHALL have a Zustand store at `features/<feature>/store/<feature>.store.tsx` defining state, actions, and selectors in a consistent pattern.

#### Scenario: Store file organization
- **WHEN** creating state management for a feature
- **THEN** the store file SHALL be at `features/<feature>/store/<feature>.store.tsx`
- **THEN** store filename SHALL match feature name (e.g., `rooms.store.tsx` for rooms feature)

#### Scenario: Store structure pattern
- **WHEN** implementing a Zustand store
- **THEN** it SHALL define:
  - typed state interface (e.g., `interface ChatState`)
  - state initialization with sensible defaults
  - actions grouped by concern (entity CRUD, UI state, etc.)
  - selectors for derived state to prevent unnecessary re-renders

#### Scenario: Store action naming
- **WHEN** creating actions in a store
- **THEN** action names SHALL follow pattern: `set<Entity>`, `add<Entity>`, `remove<Entity>`, `update<Entity>` for entity CRUD
- **THEN** UI-specific actions SHALL use `set<StateProperty>` pattern (e.g., `setFilters`, `setSelectedItem`)

### Requirement: Store synchronization with API responses
When API calls complete or WebSocket events arrive, the store SHALL be updated with new data. The synchronization logic SHALL be clear and centralized.

#### Scenario: Update store on API success
- **WHEN** an API call succeeds (in a React Query hook)
- **THEN** the store action SHALL be called to update relevant state
- **THEN** store update SHALL happen after API response, before component re-render

#### Scenario: WebSocket event synchronization
- **WHEN** a WebSocket event arrives with entity updates
- **THEN** the corresponding feature store action SHALL be called to sync the local state
- **THEN** if the entity is already cached by React Query, both stores SHALL be updated consistently

### Requirement: No direct component state mutations
Components SHALL NOT directly modify store state. Only store actions SHALL modify state.

#### Scenario: Component store interaction
- **WHEN** a component needs to update state
- **THEN** the component SHALL call store actions, never directly mutate state
- **THEN** store.setState() or other direct mutations SHALL not be used from components

### Requirement: Global state separation
Features SHALL NOT implement their own authentication or global UI state. These SHALL be managed in `/src/core/auth.store.tsx` and `/src/core/ui.store.tsx` respectively.

#### Scenario: Cross-feature global state
- **WHEN** a feature needs access to authentication state or global UI state
- **THEN** it SHALL import from `/src/core/auth.store.tsx` or `/src/core/ui.store.tsx`
- **THEN** features SHALL NOT duplicate or reimplement global concerns