## MODIFIED Requirements

### Requirement: Room list sections have refined visual styling
Section headers SHALL use a visually distinct but unobtrusive style consistent with the application design system. Room items within each section SHALL have refined hover, active, and unread badge states for improved polish. Group room items SHALL render as full-width list rows (not icon tiles) with avatar, name, and last message preview, consistent with the visual pattern of Direct Message rows.

#### Scenario: Section header is visually distinguished from room items
- **WHEN** the room list is rendered
- **THEN** section headers are identifiable as headers (distinct text style, separator, or spacing) and are not confused with room items

#### Scenario: Room item hover state is visually clear
- **WHEN** user hovers over a room item
- **THEN** a clear hover highlight is applied to the item

#### Scenario: Group section uses vertical list layout
- **WHEN** the Groups section is expanded and contains room items
- **THEN** each group room item is rendered as a full-width row in a vertical list (not a horizontal icon grid)
