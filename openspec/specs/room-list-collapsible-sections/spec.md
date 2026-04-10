# room-list-collapsible-sections Specification

## Purpose
Define requirements for the collapsible room list sidebar sections, including grouped presentation of "Groups" and "Direct Messages" rooms, section collapse/expand behaviour, section headers with room counts, session-scoped state persistence, and visual polish of room items and section headers.

## Requirements

### Requirement: Room list sections are collapsible by the user
The sidebar room list SHALL group rooms into two named sections — "Groups" and "Direct Messages" — each with a section header that users can click to collapse or expand the section. Both sections SHALL default to expanded on initial render.

#### Scenario: Both sections visible on initial load
- **WHEN** the user opens the app or navigates to the chat view for the first time in a session
- **THEN** both "Groups" and "Direct Messages" sections are expanded and all room items are visible

#### Scenario: Collapsing the Groups section hides group room items
- **WHEN** user clicks the "Groups" section header toggle
- **THEN** all group room items are hidden and a visual indicator (chevron) reflects the collapsed state

#### Scenario: Expanding the Groups section restores group room items
- **WHEN** the Groups section is collapsed and user clicks the section header again
- **THEN** all group room items are visible and the chevron reflects the expanded state

#### Scenario: Collapsing the DMs section hides DM room items
- **WHEN** user clicks the "Direct Messages" section header toggle
- **THEN** all DM room items are hidden and the section header chevron reflects the collapsed state

#### Scenario: Expanding the DMs section restores DM room items
- **WHEN** the DMs section is collapsed and user clicks the section header again
- **THEN** all DM room items are visible and the chevron reflects the expanded state

#### Scenario: Collapsing one section does not affect the other
- **WHEN** user collapses only the "Groups" section
- **THEN** "Direct Messages" section remains fully expanded with all DM items visible

### Requirement: Section headers display the section name and item count
Each section header SHALL display the section name and the total count of rooms in that section.

#### Scenario: Section header shows correct room count
- **WHEN** there are 3 group rooms and 5 DM conversations
- **THEN** the "Groups" header shows "3" and the "Direct Messages" header shows "5"

### Requirement: Section collapse state persists within the session
The collapse/expand state of each section SHALL be preserved while the user navigates within the application during the same session, but MAY reset to expanded on full page reload.

#### Scenario: State preserved on route change
- **WHEN** user collapses a section and then navigates to another route and back
- **THEN** the section remains in its collapsed state

### Requirement: Room list sections have refined visual styling
Section headers SHALL use a visually distinct but unobtrusive style consistent with the application design system. Room items within each section SHALL have refined hover, active, and unread badge states for improved polish.

#### Scenario: Section header is visually distinguished from room items
- **WHEN** the room list is rendered
- **THEN** section headers are identifiable as headers (distinct text style, separator, or spacing) and are not confused with room items

#### Scenario: Room item hover state is visually clear
- **WHEN** user hovers over a room item
- **THEN** a clear hover highlight is applied to the item
