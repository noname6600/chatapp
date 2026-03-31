## Why

The chat application codebase has grown organically with components, API calls, state, and WebSocket handlers scattered across multiple organizational patterns. As the number of features expands (chats, rooms, friends, presence), maintaining a consistent structure becomes increasingly difficult, making it harder to onboard new developers and scale features predictably. By establishing clear, enforced patterns for API organization, state management, and component hierarchy, we improve code maintainability, reduce bugs from organizational confusion, and make feature development more predictable.

## What Changes

- **Unified feature module structure**: Each feature (chats, rooms, friends, etc.) will have a consistent, self-contained folder with co-located components, hooks, API logic, store, and types
- **Separated API concerns**: Clear delineation between HTTP endpoints (.api.ts), business logic (.service.ts), and component-level data fetching
- **Centralized Zustand store organization**: Standardized store patterns with clear separation of concerns (auth, entities, UI state)
- **Enhanced type safety**: Consistent type definitions organized by domain, eliminating scattered type definitions
- **Improved component composition**: Components strictly organized by responsibility (presentational, containers, hooks)
- **WebSocket event handling standardization**: Clear patterns for socket events, listeners, and store synchronization

## Capabilities

### New Capabilities

- `feature-module-structure`: Standardized feature folder structure with components, hooks, API, store, and types co-located
- `api-layer-organization`: Clear separation between API endpoints, service business logic, and component data fetching hooks
- `zustand-store-patterns`: Standardized Zustand store organization with clear state slices and action patterns
- `type-system-organization`: Centralized type definitions organized by domain with type hierarchy patterns
- `component-hierarchy-patterns`: Clear component composition patterns with presentational components, containers, and custom hooks
- `websocket-integration-patterns`: Standardized patterns for WebSocket event handlers, listeners, and real-time store synchronization

### Modified Capabilities

- `existing-code-cleanup`: Reorganizing existing API calls, components, and state to follow new patterns

## Impact

- **Affected code**: All feature folders (chats, rooms, friends, presence, auth), API layer (/api), state management (/store), components (/components)
- **Breaking changes**: File/folder structure reorganization will require import path updates throughout the application
- **No new dependencies**: Utilizes existing tech stack (React, Zustand, Axios, Socket.io)
- **Developer workflow**: Establishes clear patterns for adding new features and modifying existing ones
