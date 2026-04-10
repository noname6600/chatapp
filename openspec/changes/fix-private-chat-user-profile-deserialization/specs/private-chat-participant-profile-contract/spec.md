## ADDED Requirements

### Requirement: Private chat participant profile decoding SHALL tolerate additive fields
The system SHALL allow private-chat startup profile consumers to deserialize participant profile responses when upstream payloads contain additional unknown JSON fields.

#### Scenario: Upstream adds username field
- **WHEN** private-chat startup fetches participant profiles and the payload includes known fields plus an additional `username` field
- **THEN** the consumer deserializes the payload without decode failure
- **AND** private chat creation continues successfully

### Requirement: Private chat startup SHALL preserve required participant profile fields
The system SHALL require `accountId`, `displayName`, and `avatarUrl` for participant profile objects used in private chat startup.

#### Scenario: Required fields present with additive fields
- **WHEN** participant profile payload includes required fields and additional unknown fields
- **THEN** startup logic uses required fields to create or load the private chat
- **AND** unknown fields are ignored for startup decisions

#### Scenario: Required field missing
- **WHEN** participant profile payload is decoded but one required field is missing
- **THEN** private-chat startup fails with the existing domain validation/error behavior
- **AND** the failure is not caused by unknown-field handling

### Requirement: Private chat startup compatibility SHALL be regression tested
The system SHALL include automated tests that verify private-chat startup remains functional with additive profile response fields.

#### Scenario: Additive-field payload in startup test
- **WHEN** automated backend tests execute private-chat startup flow using profile payloads that include unknown fields
- **THEN** tests confirm no deserialization exception is thrown
- **AND** startup flow returns the expected success outcome
