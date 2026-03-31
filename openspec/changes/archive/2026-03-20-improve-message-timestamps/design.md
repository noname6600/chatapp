## Context

The current message timestamp display uses JavaScript's date formatting to show absolute dates. Messages viewed the day after they were sent display the full date (e.g., "3/19/2026"), which requires users to mentally translate to "yesterday." This is especially relevant for active chats where message history is frequently viewed within 24 hours of sending.

The timestamp formatting logic exists in the frontend message rendering pipeline (likely a utility function or component prop in the MessageList/MessageItem components). Currently, there is no relative date handling.

## Goals / Non-Goals

**Goals:**
- Display messages from the previous calendar day with "yesterday at HH:MM" format
- Maintain backward compatibility for all other messages (today, older than yesterday)
- Ensure consistent timestamp formatting across all chat views (one-on-one, groups, all message types)
- Provide a foundation for future relative date improvements (e.g., "2 days ago")

**Non-Goals:**
- Implement full relative date formatting ("2 days ago", "3 weeks ago") — scope limited to yesterday
- Change message storage or data model (backend timestamps unchanged)
- Modify WebSocket message emission or API contracts
- Support customizable timestamp formats or user preferences

## Decisions

**Decision 1: Implement timestamp formatting in a utility function**
- **Choice**: Create a centralized `formatMessageTimestamp()` or enhance existing timestamp utility
- **Rationale**: Single source of truth for timestamp logic; easy to test and reuse across components
- **Alternatives Considered**: 
  - Implement in each MessageItem component (rejected: duplicates logic, hard to maintain)
  - Use a third-party date library like day.js (rejected: introduces dependency for simple feature)

**Decision 2: Determine "yesterday" using calendar day boundaries, not 24-hour windows**
- **Choice**: A message is "yesterday" if its date is exactly one calendar day before today in the user's timezone
- **Rationale**: Aligns with user's intuitive understanding of "yesterday"; handles edge cases like timezone changes
- **Example**: If today is 3/20/2026 at 2 AM local time, a message from 3/19/2026 at 11 PM is "yesterday"
- **Alternatives Considered**:
  - Use 24-hour sliding window (rejected: confusing when crossing midnight; wrong across timezone changes)

**Decision 3: Format as "yesterday at HH:MM" using localized time format**
- **Choice**: Use the pattern "yesterday at HH:MM" where HH:MM respects user's locale/system settings
- **Rationale**: "yesterday" is simple English; actual time respects locale
- **Note**: Requires coordination with existing i18n/localization setup; assume locale support exists

**Decision 4: Apply logic in frontend timestamp utility, not backend**
- **Choice**: Keep server timestamps as-is; apply formatting on client side during rendering
- **Rationale**: Backend is timezone-agnostic; client knows user's local timezone; reduces server logic
- **Alternative Considered**: Add formatting logic in API response (rejected: couples API to UI format)

## Risks / Trade-offs

**[Risk] Timezone edge cases** → Mitigation: Use user's local timezone for date boundary calculation; test with messages sent near midnight

**[Risk] Localization of "yesterday" word** → Mitigation: Ensure "yesterday" is translatable via i18n framework (same mechanism used for other UI text); may require backend i18n support for message search

**[Risk] Daylight Saving Time transitions** → Mitigation: JavaScript's Date object handles DST automatically; no special handling needed

**[Risk] Future scope creep** → Mitigation: Scope strictly to "yesterday"; document in spec that future features (e.g., "2 days ago") require design review

## Migration Plan

1. **No migration needed**: This is a frontend-only display change with no data model impact
2. **Deployment**: Update frontend code, redeploy chat bundle
3. **Rollback**: Revert timestamp utility to previous version
4. **No breaking changes**: Existing APIs and message storage unchanged

## Open Questions

- Should the time format (HH:MM vs HH:MM:SS) be configurable per user preference, or standardized?
- Does the existing codebase use an i18n library for "yesterday"? If not, how should the text be localized?
- Are there any other relative date improvements planned that should be coordinated with this change?
