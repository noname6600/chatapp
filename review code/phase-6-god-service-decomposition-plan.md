# Phase 6 - God-Service Decomposition Plan (Small, Service-Only)

Date: 2026-05-14
Source of truth:
- review code/service-fix-plan.md
- completed phase notes for security/correctness hardening (Phase 1A/1B/1F/2 and later)

## Scope guardrails
- Service-only extractions.
- No common redesign.
- No broad package restructure.
- One extraction at a time.
- Public behavior must remain unchanged.

## 1) Split first

Service/class to split first:
- user-service / UserProfileService

Exact responsibility to extract:
- Extract avatar metadata apply/validation flow from UserProfileService into a focused service, e.g. UserAvatarMetadataService.
- Move these responsibilities only:
  - applyAvatarMetadata(...) domain write steps
  - toAvatarAssetMetadata(...)
  - validateAvatarMetadata(...)
- Keep existing IUserProfileService public API unchanged; UserProfileService remains facade/orchestrator.

Why this extraction is now safe:
- service-fix-plan explicitly marks this as next small extraction (profile read/update vs avatar metadata assignment).
- Upload confirm hardening phase already tightened metadata trust boundary upstream.
- Internal auth hardening for user internal APIs is complete.
- Existing focused tests already isolate this surface (avatar metadata, get-self, update-profile, search-by-username).

Risk level:
- Low

Verification commands:
- ./gradlew :user-service:test --tests "com.example.user.service.impl.UserProfileServiceAvatarMetadataTest"
- ./gradlew :user-service:test --tests "com.example.user.service.impl.UserProfileServiceUpdateProfileTest" --tests "com.example.user.service.impl.UserProfileServiceGetSelfTest" --tests "com.example.user.service.impl.UserProfileServiceSearchByUsernameTest"
- ./gradlew :user-service:compileJava :user-service:compileTestJava

## 2) Second extraction (after step 1 is green)

Service/class:
- notification-service / NotificationCommandService

Exact responsibility to extract:
- Extract notification ownership/state transition logic into a focused component, e.g. NotificationReadStateService.
- Move these responsibilities only:
  - load + ownership checks for markRead/resolveActionRequired
  - action-required rule enforcement
  - markAllRead/markReadByRoom write-path state transitions
- Keep createNotification(...) and trimToLimit(...) in NotificationCommandService for now.
- Keep runAfterCommit(...) side-effect scheduling behavior unchanged.

Why this extraction is now safe:
- Phase 2 after-commit delivery hardening is complete and tested.
- Recently restored tests cover NotificationCommandService and consumer/application delegation baselines.
- This is a local separation of command-state rules from side-effect dispatch, without architecture change.

Risk level:
- Low to Medium

Verification commands:
- ./gradlew :notification-service:test --tests "com.example.notification.service.impl.NotificationCommandServiceTest" --tests "com.example.notification.controller.NotificationControllerTest"
- ./gradlew :notification-service:test --tests "com.example.notification.application.NotificationKafkaEventApplicationServiceTest" --tests "com.example.notification.kafka.NotificationKafkaConsumersTest"
- ./gradlew :notification-service:compileJava :notification-service:compileTestJava

## 3) Third extraction (after step 2 is green)

Service/class:
- chat-service / RoomService

Exact responsibility to extract:
- Extract membership mutation use case orchestration from RoomService into a focused component, e.g. RoomMembershipMutationService.
- Move these responsibilities only:
  - joinByInviteRoomId(...)
  - addMember(...)
  - removeMember(...)
  - banMember(...)
  - unbanMember(...)
  - transferOwnership(...)
- Keep room metadata/avatar and last-message projection methods in RoomService for this phase.
- Keep existing cache invalidation + realtime event semantics exactly as-is.

Why this extraction is now safe:
- Critical room authorization hardening was completed earlier (membership guard + protected room-scoped paths).
- This split aligns with service-fix-plan guidance to split RoomService around membership authorization boundaries only.
- Extraction boundary is use-case cohesive and testable without endpoint/protocol redesign.

Risk level:
- Medium

Verification commands:
- ./gradlew :chat-service:test --tests "com.example.chat.modules.room.service.impl.RoomServiceTest" --tests "com.example.chat.modules.room.service.impl.RoomServiceInviteJoinIntegrationTest"
- ./gradlew :chat-service:test --tests "com.example.chat.modules.room.controller.RoomControllerAuthorizationTest" --tests "com.example.chat.modules.message.application.query.MessageQueryServiceTest" --tests "com.example.chat.modules.message.application.pipeline.reaction.steps.PersistReactionStepTest"
- ./gradlew :chat-service:compileJava :chat-service:compileTestJava

## 4) Why this order
- Step 1 (user-service) gives high maintainability gain with the lowest behavioral risk.
- Step 2 (notification-service) isolates transition rules while preserving already-hardened after-commit side effects.
- Step 3 (chat-service RoomService) is highest-friction/highest-surface and is intentionally deferred until prior low-risk splits are green.

## 5) Out of scope for this phase
- Common module changes.
- Cross-service contract redesign.
- Broad package moves/renaming.
- Websocket cutover changes.
