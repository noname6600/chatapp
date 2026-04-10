## ADDED Requirements

### Requirement: Clean migration of existing code
All existing code from the old structure SHALL be migrated to the new feature-based structure. No functionality SHALL be lost during migration.

#### Scenario: Complete code migration
- **WHEN** refactoring existing features (auth, chats, rooms, friends, presence, profile, notifications)
- **THEN** all existing code SHALL be moved to the corresponding `/features/<feature>/` folder
- **THEN** functionality SHALL remain unchanged, code SHALL only be reorganized
- **THEN** all code SHALL continue to work after migration

#### Scenario: Import path updates
- **WHEN** migrating code to new structure
- **THEN** all import paths throughout the application SHALL be updated
- **THEN** imports referencing old paths SHALL be updated to new feature-based paths
- **THEN** public exports via `index.ts` MAY simplify some import paths

### Requirement: Remove old folder structure
After successful migration, old organizational folders SHALL be removed to avoid confusion and maintain single source of truth.

#### Scenario: Cleanup old API folder
- **WHEN** all API logic is migrated to feature folders
- **THEN** `/src/api/` folder SHALL be removed
- **THEN** any remaining shared API utilities SHALL be moved to `/src/core/` or `/src/utils/`

#### Scenario: Cleanup old components folder
- **WHEN** all feature-specific components are migrated to feature folders
- **THEN** `/src/components/` folder SHALL contain only `/src/components/shared/`
- **THEN** feature-specific component folders SHALL be removed
- **THEN** old component organization by domain SHALL be eliminated

#### Scenario: Store migration
- **WHEN** all feature stores are migrated to feature folders
- **THEN** `/src/store/` SHALL contain only `/src/core/auth.store.tsx` and `/src/core/ui.store.tsx`
- **THEN** feature-specific stores SHALL be removed from old location
- **THEN** all imports SHALL reference new feature store locations

### Requirement: No dead code
After cleanup, the codebase SHALL not contain duplicate implementations or unreferenced code from the migration.

#### Scenario: Consolidate duplicate logic
- **WHEN** code was duplicated during organic growth
- **THEN** duplicates SHALL be identified and consolidated into shared utilities
- **THEN** all references SHALL point to single source of truth

#### Scenario: Remove unreferenced code
- **WHEN** cleanup is complete
- **THEN** no unreferenced or orphaned code files SHALL remain
- **THEN** TypeScript strict mode and linting SHALL verify no broken imports

### Requirement: Backward compatibility during transition
During refactoring, a transition period MAY maintain compatibility to allow phased updates.

#### Scenario: Re-export from old locations (optional)
- **WHEN** migrating code incrementally
- **THEN** old import paths MAY be maintained as re-exports to new locations
- **THEN** deprecation warnings SHOULD be added to encourage migration
- **THEN** eventually old re-export paths SHALL be removed after all usages are updated
