## ADDED Requirements

### Requirement: Feature-scoped type definitions
Each feature SHALL define its domain types in `features/<feature>/types/` with types organized by entity or concern. Types SHALL be co-located with the feature implementation.

#### Scenario: Type file organization
- **WHEN** organizing types for a feature
- **THEN** types SHALL be in `features/<feature>/types/` directory
- **THEN** files SHALL be named by domain (e.g., `messages.ts`, `rooms.ts`, `users.ts`) not by type category (avoid `interfaces.ts`, `models.ts`)

#### Scenario: Type file exports
- **WHEN** implementing types in `features/<feature>/types/<domain>.ts`
- **THEN** types SHALL be exported from `features/<feature>/index.ts` for easy importing
- **THEN** components and hooks import types from the feature package, not from nested paths

#### Scenario: Core types sharing
- **WHEN** a type is needed across multiple features
- **THEN** the shared type SHALL be defined in `/src/core/types/` or `/src/types/` (global types)
- **THEN** each feature can import and extend global types via feature types

### Requirement: Type hierarchy and inheritance
Types that build on each other SHALL form a clear hierarchy. API response types, store state types, and display types SHALL be organized to show their relationships.

#### Scenario: API response typing
- **WHEN** defining types for API responses
- **THEN** response types SHALL be in feature types (e.g., `features/chats/types/messages.ts`)
- **THEN** HTTP response types and domain model types SHALL be distinguished with naming convention

#### Scenario: Entities vs DTOs
- **WHEN** distinguishing between data transfer objects and domain models
- **THEN** API response types SHALL be clearly named (e.g., `MessageDTO`)
- **THEN** domain types used internally SHALL use simple names (e.g., `Message`)

### Requirement: No duplicate type definitions
Type definitions for the same entity SHALL exist in only one location. Shared types at `/src/types/` or core types, not duplicated across features.

#### Scenario: Consolidating duplicate types
- **WHEN** same type exists in multiple feature folders
- **THEN** the type SHALL be moved to `/src/types/` or `/src/core/types/`
- **THEN** all features SHALL import from the shared location
- **THEN** feature-specific type extensions SHALL be done in the feature's types folder

#### Scenario: Type reuse across features
- **WHEN** a feature needs to use a type from another domain
- **THEN** the type SHALL be imported from the source feature's public exports (`index.ts`)
- **THEN** unnecessary duplication SHALL be eliminated
