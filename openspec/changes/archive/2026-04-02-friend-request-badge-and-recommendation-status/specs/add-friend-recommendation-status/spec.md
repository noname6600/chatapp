## ADDED Requirements

### Requirement: Recommendation Item Status Consistency
The system SHALL render each recommended user with a status that reflects the latest known friendship/request state.

#### Scenario: Pending after send request
- **WHEN** the current user sends a friend request from a recommendation item
- **THEN** that item MUST transition to a pending-request status and disable duplicate send action

#### Scenario: Accepted status update
- **WHEN** a pending request for a recommended user becomes accepted
- **THEN** the recommendation item MUST transition to friend status

#### Scenario: Rejected or canceled status update
- **WHEN** a pending request for a recommended user is rejected or canceled
- **THEN** the recommendation item MUST transition back to a send-request-available status

### Requirement: Remove Nonessential Realtime Recommendation Mutations
The system SHALL avoid realtime recommendation list mutations that are unrelated to friend request notification and status transitions.

#### Scenario: Ignore unrelated realtime signals
- **WHEN** a realtime event does not represent friend request notification or recommendation status transition
- **THEN** the add-friend recommendation list MUST remain unchanged
