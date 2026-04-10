## ADDED Requirements

### Requirement: WebSocket handler organization
WebSocket event handlers for a feature SHALL be co-located with the feature in `features/<feature>/websocket/` directory.

#### Scenario: WebSocket file organization
- **WHEN** implementing real-time handlers for a feature
- **THEN** socket event handlers SHALL be in `features/<feature>/websocket/`
- **THEN** files SHALL follow pattern: `<event-name>.socket.ts` (handler) or `<event-name>.listener.ts` (setup)

#### Scenario: Socket event handler structure
- **WHEN** creating a socket event handler
- **THEN** handler file SHALL export a function that takes the socket instance and store actions
- **THEN** handler SHALL attach event listeners to socket events specific to that feature
- **THEN** handler SHALL call store actions to synchronize state when events arrive

### Requirement: WebSocket events synchronize with Zustand stores
When a WebSocket event arrives with entity data, the feature's Zustand store SHALL be updated to keep real-time data synchronized.

#### Scenario: Real-time message handling
- **WHEN** a new message arrives via WebSocket
- **THEN** the `chats.store.tsx` shall be updated with the message
- **THEN** the component using `useChatMessages()` hook shall re-render with new message
- **THEN** React Query cache SHALL also be updated to maintain consistency

#### Scenario: Presence tracking
- **WHEN** presence information arrives (user online/offline)
- **THEN** `presence.store.tsx` SHALL be updated
- **THEN** any component using presence data SHALL re-render synchronously

### Requirement: No direct component socket connections
Components SHALL NOT directly connect to WebSocket or attach event listeners. All real-time communication SHALL happen through feature sockets and store synchronization.

#### Scenario: Socket lifecycle management
- **WHEN** a feature initializes
- **THEN** socket event handlers SHALL be registered during app initialization
- **THEN** event handlers SHALL remain active for the feature lifetime
- **THEN** cleanup (removing listeners) SHALL happen on app shutdown

#### Scenario: Feature-specific socket events
- **WHEN** components need real-time updates
- **THEN** components SHALL subscribe to store changes via selectors
- **THEN** store changes happen automatically when WebSocket events arrive
- **THEN** no component SHALL directly listen to socket events

### Requirement: Store and API consistency
Data from API responses and WebSocket events SHALL be kept consistent. When the same entity arrives via both channels, conflicts SHALL be resolved with clear rules.

#### Scenario: Update conflict resolution
- **WHEN** an entity is updated via API AND receives update via WebSocket
- **THEN** WebSocket updates SHALL take precedence as real-time source of truth
- **THEN** optimistic updates in React Query SHALL be invalidated if WebSocket data differs

#### Scenario: Offline state handling
- **WHEN** socket connection is lost
- **THEN** store SHALL remain valid and components continue working with cached data
- **WHEN** socket reconnects
- **THEN** store SHALL re-sync with server data via API refresh