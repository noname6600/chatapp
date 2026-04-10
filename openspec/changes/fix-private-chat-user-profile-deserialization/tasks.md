## 1. Locate and Harden Decode Boundaries

- [x] 1.1 Identify the exact private-chat startup profile consumer DTO(s) and Feign client response path where unknown `username` currently throws decode errors.
- [x] 1.2 Apply local tolerant deserialization handling (for example, ignore unknown properties) to the identified profile consumer DTO(s) without changing global ObjectMapper behavior.
- [x] 1.3 Verify required participant fields (`accountId`, `displayName`, `avatarUrl`) remain required for private-chat startup logic.

## 2. Preserve Startup Behavior and Error Semantics

- [x] 2.1 Ensure private chat creation from friend-click flow succeeds when profile payload includes additive fields.
- [x] 2.2 Ensure missing required fields still produce existing domain validation/error outcomes.
- [x] 2.3 Add or update logging/diagnostics only as needed to distinguish required-field failures from unknown-field compatibility.

## 3. Regression Coverage

- [x] 3.1 Add backend test coverage for additive-field profile payloads in private-chat startup decode path.
- [x] 3.2 Add a negative test for missing required profile field(s) to confirm behavior is unchanged.
- [x] 3.3 Run affected module tests and capture evidence that private-chat startup no longer fails on unknown fields.

## 4. Validation and Release Readiness

- [x] 4.1 Validate OpenSpec change status is apply-ready and all required artifacts are complete.
- [x] 4.2 Document touched services/modules and rollout notes for a backward-compatible patch release.

## 5. Rollout Notes

- Touched modules/services: chat-service only.
- Changed files: private-room profile consumer DTO, private-room startup service validation/diagnostics, and new chat-service regression tests.
- Deployment type: backward-compatible patch; no database migration.
- Rollback: revert these chat-service code and test changes together.
