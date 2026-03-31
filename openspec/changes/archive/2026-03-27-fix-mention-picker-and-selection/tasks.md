## 1. Username Availability and Fetch Traceability

- [x] 1.1 Ensure user bulk response includes username and frontend store maps it without loss.
- [x] 1.2 Add temporary trace logs at backend bulk endpoint/service and frontend fetch/API call path to verify id/username/displayName propagation.
- [x] 1.3 Verify cached-user short-circuit path still surfaces trace information when bulk call is skipped.

## 2. Mention Selection and Send Reliability

- [x] 2.1 Keep mention candidate filtering dual-field (display name + username) with max 5 cap.
- [x] 2.2 Guarantee mention selection stores canonical mention target (`userId`, `username`, `displayName`) and never inserts undefined token text.
- [x] 2.3 Validate message send succeeds after click and keyboard mention selection.

## 3. Mention Display and Interaction in Message List

- [x] 3.1 Render mention labels as `@DisplayName` in plain-text message rendering path.
- [x] 3.2 Render mention labels as `@DisplayName` in structured block rendering path.
- [x] 3.3 Make mention tokens clickable and open user profile popup with avatar/name context.

## 4. Mention Highlight Policy

- [x] 4.1 Highlight full message row when current user is mentioned.
- [x] 4.2 Highlight only mention token for mentions that do not target current user.
- [x] 4.3 Confirm mention highlight behavior coexists with existing reply-linked highlight rules.

## 5. Validation and Cleanup

- [x] 5.1 Add/extend unit and interaction tests for display-name mention rendering and clickable mention popup.
- [x] 5.2 Add/extend tests for self-mention row highlight vs non-self token-only highlight.
- [ ] 5.3 Perform manual verification in UI for mention send, rendering, popup, and highlight behavior.
- [x] 5.4 Remove or gate temporary debug logs after behavior is verified.
