## ADDED Requirements

### Requirement: Settings-style friends page shell
The friends page SHALL present friend management inside a polished, settings-style shell with stronger visual hierarchy, summary context, and sectioned content panels.

#### Scenario: Friends page matches the settings-level surface quality
- **WHEN** a user opens the friends page
- **THEN** the page SHALL render inside a rounded, carded container with a clear page header and bordered content sections
- **THEN** the page SHALL include summary or context panels that make the friends experience feel visually aligned with the settings page

#### Scenario: Tab content remains organized inside the upgraded shell
- **WHEN** a user switches between online, all, pending, and add-friend views
- **THEN** the active tab content SHALL stay inside the same page shell
- **THEN** each tab SHALL preserve its own relevant counts, labels, and section structure without collapsing into an ungrouped list

### Requirement: Friend cards support direct-chat launch
Established friend entries SHALL provide a direct path into private chat from the friends page.

#### Scenario: Clicking a friend card opens direct chat
- **WHEN** a user clicks the primary surface of a friend card in the online or all tab
- **THEN** the frontend SHALL create or resolve a private room using the existing private-chat flow
- **THEN** the active chat room SHALL be selected
- **THEN** the user SHALL be navigated to the chat page

#### Scenario: Primary chat action on the right opens direct chat
- **WHEN** a user clicks the dedicated chat action button on the right side of a friend card
- **THEN** the same direct-chat launch flow SHALL run
- **THEN** the button SHALL provide a discoverable shortcut without requiring the whole card to be clicked

### Requirement: Friend card secondary actions remain distinct from chat launch
Friend-card secondary actions SHALL remain usable without accidentally triggering direct-chat navigation.

#### Scenario: More actions do not trigger row navigation
- **WHEN** a user clicks a more-menu, remove action, or other secondary control within a friend card
- **THEN** the secondary action SHALL execute without opening chat
- **THEN** the row click behavior SHALL be suppressed for that interaction

#### Scenario: Pending request actions stay button-driven
- **WHEN** a user interacts with incoming or outgoing pending request rows
- **THEN** accept, decline, and cancel controls SHALL remain explicit button actions
- **THEN** pending rows SHALL NOT attempt to open direct chat as their primary action
