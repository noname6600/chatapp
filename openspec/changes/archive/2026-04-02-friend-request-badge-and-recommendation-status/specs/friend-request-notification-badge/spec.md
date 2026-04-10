## ADDED Requirements

### Requirement: Friend Request Badge Updates
The system SHALL update the friend request notification badge when friend request events relevant to the current user are received.

#### Scenario: Incoming request increments badge
- **WHEN** another user sends a friend request to the current user
- **THEN** the notification badge count MUST increment by one for the current user

#### Scenario: Reconcile badge from unread source
- **WHEN** the add-friend page initializes or reconnects after connection loss
- **THEN** the client MUST reconcile badge count from the unread-count API response

### Requirement: Outgoing Request Notification Feedback
The system SHALL provide notification feedback when the current user sends a friend request.

#### Scenario: Send request confirmation notification
- **WHEN** the current user successfully sends a friend request
- **THEN** the UI MUST show an outgoing request notification state without incrementing incoming unread badge count
