## 1. Backend Initiation Contract

- [ ] 1.1 Locate and fix the private chat create-or-open service path so initiation always resolves to one room id.
- [ ] 1.2 Add eligibility validations for target user (self-target, unknown user, disallowed target) with stable error categories.
- [ ] 1.3 Ensure API response payload for successful initiation always includes a routable room identifier.
- [ ] 1.4 Add backend tests for existing-room path, create-new path, and rejection paths.

## 2. Frontend Initiation Flow

- [ ] 2.1 Update the private chat start action in friend/profile entry points to call the gateway initiation endpoint through the shared API client.
- [ ] 2.2 Map initiation success responses to deterministic room navigation using returned room id.
- [ ] 2.3 Map initiation failure categories to actionable user-facing feedback (no silent failure).
- [ ] 2.4 Add frontend tests for success navigation and initiation failure feedback behavior.

## 3. Gateway Client and Error Handling Alignment

- [ ] 3.1 Verify private chat initiation requests use centralized gateway routing and authenticated client configuration.
- [ ] 3.2 Validate API client handling for 401 refresh-and-retry behavior remains intact for initiation calls.
- [ ] 3.3 Add/adjust API client tests to assert stable error-category propagation for initiation failures.

## 4. Verification and Rollout Readiness

- [ ] 4.1 Run targeted backend and frontend test suites covering private chat initiation flows.
- [ ] 4.2 Perform manual end-to-end smoke checks for starting private chat with valid and invalid targets.
- [ ] 4.3 Document rollout notes and fallback expectations for private chat initiation regressions.
