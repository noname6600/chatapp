## 1. Timestamp Utility Implementation

- [x] 1.1 Create or locate the timestamp formatting utility function in the codebase
- [x] 1.2 Add helper function to determine if a date is "yesterday" based on user's local timezone
- [x] 1.3 Implement `getYesterdayLabel()` function to retrieve localized "yesterday" string via i18n
- [x] 1.4 Update timestamp format logic to return "yesterday at HH:MM" for previous day messages
- [x] 1.5 Ensure time format (HH:MM vs HH:MM:SS) respects user locale settings
- [x] 1.6 Handle edge case: messages sent near midnight/timezone boundaries

## 2. Localization Setup

- [ ] 2.1 Add "yesterday" string to i18n translation files (English default)
- [ ] 2.2 Add translations for "yesterday" in all supported languages (Spanish, French, German, etc.)
- [ ] 2.3 Verify i18n integration works with timestamp utility (test with multiple locales)

## 3. Component Integration

- [x] 3.1 Identify all message display components that render timestamps
- [x] 3.2 Update MessageItem or equivalent component to use new timestamp utility
- [x] 3.3 Update message list views (one-on-one chats, group chats, any other contexts)
- [ ] 3.4 Test timestamp display across all message types and chat contexts
- [x] 3.5 Verify original timestamp display for today's and older messages (unchanged)

## 4. Testing

- [x] 4.1 Write unit tests for "yesterday" date detection logic
- [x] 4.2 Write unit tests for timezone boundary cases (messages across midnight)
- [x] 4.3 Write unit tests for localization/i18n integration
- [x] 4.4 Write unit tests for time format locale-specific behavior
- [ ] 4.5 Integration test: verify "yesterday" appears correctly in message list
- [ ] 4.6 Integration test: verify today's messages still display original format
- [ ] 4.7 Integration test: verify old messages still display original format

## 5. Build & Verification

- [x] 5.1 Run frontend build (npm run build) with no errors
- [x] 5.2 Run all unit and integration tests locally (ensure all pass)
- [x] 5.3 TypeScript compilation check (no type errors)
- [ ] 5.4 Manual browser test: verify "yesterday" displays for previous day messages
- [ ] 5.5 Manual browser test: verify time format respects system locale
- [ ] 5.6 Manual browser test: verify no console errors in development mode
