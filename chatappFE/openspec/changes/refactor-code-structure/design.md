## Context

The current codebase has grown with features distributed across different organizational patterns:
- API calls mixed in `/api` folder without clear separation between endpoints and business logic
- Zustand stores in `/store` with inconsistent patterns and unclear state slices
- Components spread across `/components` organized by domain but not forming cohesive feature modules
- WebSocket handlers in `/websocket` disconnected from their corresponding features
- Type definitions scattered across `/types` with some duplicated or related types separated
- Features-specific code starting to appear in `/features` (chats, rooms) but not all features follow this pattern

This inconsistency makes it difficult for developers to know where to add new code, results in duplicate logic, and increases maintenance burden as the codebase grows.

## Goals / Non-Goals

**Goals:**
- Establish a unified feature module structure where each feature (auth, chats, rooms, friends, presence, notifications, profile) is self-contained with clear responsibility boundaries
- Implement clear API layer patterns: HTTP endpoints (`.api.ts`), business logic (`.service.ts`), and React Query hooks (`.hooks.ts`)
- Standardize Zustand store organization with consistent state slices, actions, and selectors per feature
- Centralize and organize type definitions with clear domain hierarchies
- Create clear guidelines for component composition (presentational, container, custom hook patterns)
- Integrate WebSocket handlers within their feature modules with clear synchronization with Zustand stores

**Non-Goals:**
- Changing the underlying technologies (React, Zustand, Axios, Socket.io remain the same)
- Creating new features during refactoring - this is structure-only
- Modifying business logic or adding new functionality
- Changing the API contracts with the backend

## Decisions

### Decision 1: Feature Module Colocation

**Decision**: Each feature (auth, chats, rooms, friends, presence, profile, notifications) will have a dedicated folder at `/src/features/<feature-name>/` containing:
- `components/` - all React components for that feature
- `hooks/` - custom hooks (data fetching, local state, WebSocket listeners)
- `store/` - Zustand store definition with state and actions
- `types/` - TypeScript type definitions scoped to this feature
- `api/` - HTTP endpoints and services for this feature
- `websocket/` - WebSocket event handlers for this feature (if applicable)
- `index.ts` - public exports and re-exports

**Rationale**: Colocation makes features self-contained and easier to understand. A developer working on chat features can find all related code in one place, reducing cognitive load and import path complexity.

**Alternatives Considered**:
- Keep API in separate `/api` folder - rejected because it creates distributed feature logic
- Separate `/components` and `/store` by feature - rejected because it's harder to track what belongs together

### Decision 2: Three-Layer API Organization

**Decision**: Each feature's API layer will follow this pattern:
- `*.api.ts` - Low-level HTTP client functions that directly call backend endpoints
- `*.service.ts` - Business logic and data transformation layer
- Custom hooks in `hooks/` that use React Query for data fetching and caching

**Rationale**: Clear separation allows easy testing of each layer independently, makes API contracts explicit, and centralizes business logic transformations.

**Alternatives Considered**:
- Single thick API layer - rejected because it becomes a monolith
- No business logic layer - rejected because it couples components to API contracts

### Decision 3: Store Organization by Feature

**Decision**: Each feature has a Zustand store at `features/<feature>/store/<feature>.store.tsx` that:
- Defines typed state interface
- Groups related actions together
- Uses selectors for derived state
- Handles synchronization with API responses and WebSocket events

**Rationale**: Feature-scoped stores keep state concerns isolated. Selectors prevent unnecessary re-renders in components.

### Decision 4: Cross-Feature Concerns

**Decision**: Global concerns that span multiple features remain in `/src/core/`:
- `auth.ts` - Authentication state and actions
- `ui.ts` - UI-level state (overlays, notifications, modals)
- Shared utilities and configurations

**Rationale**: Some state is genuinely global (authentication) and attempting to couple it to a single feature creates artificial dependencies.

### Decision 5: Shared Components and Utilities

**Decision**:
- Reusable UI components go in `/src/components/shared/`
- Domain-agnostic utilities go in `/src/utils/`
- Shared hooks go in `/src/hooks/shared/`

**Rationale**: Prevents duplication while keeping feature modules lightweight. Shared code is still organized by type (components, hooks, utils) for clarity.

## Risks / Trade-offs

**[Risk]** Large refactoring can introduce import path bugs → **[Mitigation]** Use TypeScript strict mode and ESLint to catch broken imports; systematically update imports during refactoring phases

**[Risk]** Circular dependencies between features → **[Mitigation]** Enforce strict dependency rules: features can only depend on `/core` and `/components/shared`; no feature-to-feature dependencies

**[Risk]** Refactoring could temporarily break functionality → **[Mitigation]** Refactor incrementally by feature; test each feature module after restructuring before moving to the next

**[Risk]** Team members unfamiliar with new structure → **[Mitigation]** Document folder structure clearly; include index.ts re-exports for easy discovery

**[Trade-off]** More folders/files for organization vs. slightly more overhead for imports → **[Rationale]** The organizational benefit outweighs the minor import path increase

## Migration Plan

1. **Phase 1 - Preparation**: Document current state, create new folder structure as templates
2. **Phase 2 - Core First**: Refactor /core (auth, ui state) and establish shared utilities
3. **Phase 3 - Features**: Migrate each feature module (auth → chats → rooms → friends → presence → profile → notifications)
4. **Phase 4 - Cleanup**: Remove old folders, consolidate duplicates, update all import paths
5. **Phase 5 - Verification**: Run tests, fix any broken imports, validate applications builds and runs correctly

Rollback strategy: Git branches per phase with clear checkpoints. If issues arise, can revert to any phase midway.

## Open Questions

- Should old `/api` and `/components` folders be completely removed or kept for backward compatibility during transition?
- Do we need additional shared utilities beyond what exists in `/utils`?
- Should there be a shared hooks folder for hooks used across features?
