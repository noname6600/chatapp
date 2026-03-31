## ADDED Requirements

### Requirement: Scroll position is preserved after prepending older messages
The system SHALL restore the user's scroll position after asynchronously fetching and prepending older messages, so the reading context is not disrupted.

#### Scenario: Scroll offset maintained after prepend
- **WHEN** user scrolls to top and triggers older message fetch with current scroll height H
- **THEN** after older messages are prepended and DOM updates, scroll position is restored so the user sees the same messages they were reading before the prepend

#### Scenario: Scroll restoration calculation
- **WHEN** prepending 20 older messages into the view with document height increase of dH
- **THEN** scrollTop is adjusted by dH pixels to keep the previously-visible content in the same viewport position

#### Scenario: Restoration handles rapid scroll
- **WHEN** user continues scrolling while a prepend operation is in progress
- **THEN** restoration is deferred or cancelled, and the user's new scroll position takes precedence

#### Scenario: Edge case: prepend at very top
- **WHEN** prepending messages and current scrollTop is 0
- **THEN** scrollTop remains at 0; no restoration calculation is applied since user is already at absolute top

### Requirement: Visual feedback during scroll-load
The system MAY display loading indicators or visual cues when fetching older messages during scroll-load.

#### Scenario: Loading state is shown
- **WHEN** older message fetch is initiated via scroll-to-top
- **THEN** system may display a loading indicator (e.g., spinner, text) to indicate fetching is in progress
