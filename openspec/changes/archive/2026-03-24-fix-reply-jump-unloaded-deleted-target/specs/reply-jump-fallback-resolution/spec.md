## ADDED Requirements

### Requirement: Reply jump SHALL resolve unloaded targets with bounded fallback search
When the replied-to target is not currently loaded, the system SHALL execute a bounded fallback search before failing jump resolution.

#### Scenario: Resolver finds target after backfill
- **WHEN** user clicks a reply snippet and the target is not in current DOM or first around-query result
- **THEN** the system loads older context in bounded steps
- **AND** stops as soon as target message id is found
- **AND** scrolls to target with temporary jump emphasis

#### Scenario: Resolver stops at budget limit
- **WHEN** fallback search reaches configured request budget without finding target
- **THEN** the system stops requesting additional history
- **AND** sets reply target state to unavailable

#### Scenario: Resolver stops when no older history remains
- **WHEN** fallback search observes `hasOlder=false` before target is found
- **THEN** the system terminates search immediately
- **AND** sets reply target state to unavailable

### Requirement: Reply jump fallback SHALL preserve list stability
The system SHALL preserve list integrity and viewport behavior during fallback search.

#### Scenario: No duplicate messages while backfilling
- **WHEN** fallback search prepends older pages
- **THEN** message merge preserves unique message ids and sequence ordering

#### Scenario: No top snap before target resolution
- **WHEN** fallback search is in progress
- **THEN** viewport does not snap to room top unexpectedly
- **AND** smooth scroll occurs only after target is present in DOM

### Requirement: Reply jump terminal state SHALL communicate deleted/unavailable originals
The system SHALL expose a clear terminal state when original target cannot be resolved.

#### Scenario: Deleted or inaccessible target shows unavailable state
- **WHEN** resolver exhausts all lookup paths without finding the target message
- **THEN** reply preview indicates original message is unavailable or deleted
- **AND** the viewport position remains unchanged (no jump is performed)
- **AND** the UI remains interactive without runtime errors

### Requirement: Successful reply jump SHALL preserve bidirectional pagination from landing context
After resolving a target that was initially unloaded, the resulting window SHALL support normal continuation in both directions.

#### Scenario: User can load older messages after jump
- **WHEN** resolver lands on target context and user scrolls upward
- **THEN** older messages can be fetched and prepended from that context

#### Scenario: User can load newer messages after jump
- **WHEN** resolver lands on target context and user scrolls downward toward latest
- **THEN** newer messages can be fetched/appended from that context
