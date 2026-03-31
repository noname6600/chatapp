## 1. Context and Pipeline Refactor

- [ ] 1.1 Remove roomId field from ToggleReactionContext
- [ ] 1.2 Update all reaction pipeline steps to stop reading roomId from context
- [ ] 1.3 Ensure required room scope is derived from persisted message data by messageId
- [ ] 1.4 Compile chat-service module and fix all refactor fallout

## 2. Event Publishing and Routing

- [ ] 2.1 Update reaction publish step to resolve routing room from authoritative message source
- [ ] 2.2 Remove any fallback logic that uses context roomId
- [ ] 2.3 Verify emitted reaction payload and event metadata remain backward compatible

## 3. Controller and Service Compatibility

- [ ] 3.1 Review reaction toggle controller/service signatures for roomId coupling
- [ ] 3.2 Remove internal roomId wiring where redundant while preserving endpoint behavior
- [ ] 3.3 Validate reaction add/remove semantics are unchanged for clients

## 4. Automated Testing

- [ ] 4.1 Update unit tests for ToggleReactionContext and reaction pipeline steps
- [ ] 4.2 Add test: pipeline executes without context roomId
- [ ] 4.3 Add test: publish step derives room from message entity
- [ ] 4.4 Add regression test: add/remove reaction behavior remains unchanged

## 5. Verification

- [ ] 5.1 Run chat-service test suite for reaction pipeline
- [ ] 5.2 Run full backend build for chat-service
- [ ] 5.3 Manual smoke test: toggle reaction add/remove from frontend and verify realtime events still route correctly
