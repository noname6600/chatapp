## ADDED Requirements

### Requirement: Messages from yesterday display with relative date format
The system SHALL display messages sent on the previous calendar day with the format "yesterday at HH:MM" instead of an absolute date. This applies to messages in one-on-one chats, group chats, and all other message contexts.

#### Scenario: Message from previous calendar day displays "yesterday"
- **WHEN** viewing a message sent on the previous calendar day in the user's local timezone
- **THEN** the message timestamp displays as "yesterday at HH:MM" where HH:MM is the message send time

#### Scenario: Message from today displays original format
- **WHEN** viewing a message sent on the current calendar day
- **THEN** the message timestamp displays in its original format (unchanged)

#### Scenario: Message older than yesterday displays original format
- **WHEN** viewing a message sent two or more calendar days ago
- **THEN** the message timestamp displays in its original format (unchanged)

#### Scenario: Timezone boundary respected for "yesterday" determination
- **WHEN** a message is sent at 11 PM UTC and viewed the next calendar day in UTC-5 timezone
- **THEN** the message correctly displays as "yesterday" based on the user's local timezone, not UTC

#### Scenario: Time format respects user locale
- **WHEN** viewing a message from yesterday in a region using 24-hour time format
- **THEN** the timestamp displays as "yesterday at 14:30" (24-hour format)
- **WHEN** viewing a message from yesterday in a region using 12-hour time format
- **THEN** the timestamp displays as "yesterday at 2:30 PM" (12-hour format with AM/PM)

#### Scenario: Consistent formatting across chat types
- **WHEN** a user views messages in one-on-one chats, group chats, or any message list
- **THEN** messages from yesterday consistently display as "yesterday at HH:MM" in all contexts

### Requirement: "Yesterday" text is localizable
The system SHALL ensure the text "yesterday" used in the timestamp format is translatable via the application's internationalization (i18n) framework, allowing support for multiple languages.

#### Scenario: Yesterday text displays in user's language
- **WHEN** a user's language preference is set to Spanish
- **THEN** messages from yesterday display as "ayer a HH:MM" (or equivalent Spanish translation)

#### Scenario: Fallback to English if translation unavailable
- **WHEN** a language translation for "yesterday" is not available
- **THEN** the system falls back to English "yesterday"

### Requirement: Timestamp calculation uses client time, not server time
The system SHALL calculate whether a message is from "yesterday" using the client's local timezone, not server time. This ensures consistency with the user's perception of "today" and "yesterday."

#### Scenario: Timestamp calculation accounts for local timezone
- **WHEN** a message timestamp is evaluated for display
- **THEN** the calculation uses the client's current timezone to determine if the message is from the previous calendar day
