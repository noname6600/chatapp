# Service Structure Readiness

Date: May 13, 2026
Scope: service-only structural cleanup and runtime-safe refactor

## Verdict

The backend is now structurally cleaner and safer to validate.

It is not fully polished, but it is clean enough to continue with credible runtime validation.

## What is materially better now

1. The proven runtime blockers are fixed.
- Kafka producer bean mismatch is resolved service-locally.
- common-websocket BOM import issue is removed.
- presence websocket disconnect cleanup now reaches offline state updates.
- realtime-edge now uses the correct server.port configuration.
- notification friendship Kafka consumer now follows the active aggregate topic contract.

2. Active paths are easier to identify.
- user-service account-created handling now has a single application-service orchestration path.
- user-service no longer compiles directly against upload-service.
- auth-service now separates public and authenticated auth endpoints more clearly.
- gateway websocket routing posture is documented instead of implicit.
- friendship-service no longer carries an unused outbound websocket publisher path beside its rollback websocket ingress.
- chat-service no longer carries a second, unused room application-service architecture.

3. Dead runtime noise is reduced.
- Unused interfaces and services that were not referenced by main code or tests were removed.
- The old realtime-edge skeleton tree is gone.
- The deprecated notification realtime adapter is gone.

## Runtime confidence

Runtime confidence is higher than before this pass.

Why:
- compile sweep across all scoped services succeeded
- focused auth, user, and realtime-edge tests passed
- friendship, presence, and notification test sources compile after cleanup
- rollback-compatible endpoints were preserved instead of being removed aggressively

## What remains important vs optional

Still important:
1. Run end-to-end validation now that the service layer is cleaner.
2. Resolve stale chat-service realtime-contract tests if they still matter to the team, because they currently reference missing old common APIs.
3. Revisit notification-service consumer orchestration if you want main-code paths to consistently delegate through application services.
4. Revisit auth/user DatabaseSchemaFixer runtime debt when migration state is known.

Optional cleanup:
1. Further split chat RoomService, but only with an explicit rewiring plan for active controller and message pipeline dependencies.
2. Normalize more package naming between ingress, application, and infrastructure layers across services.
3. Revisit gateway websocket routing when realtime-edge ingress ownership is ready to move.

## Is the backend structurally clean enough now?

Yes, for the current phase.

Not because every service is elegant, but because:
- real blockers are addressed first
- dead parallel architectures were removed where they were provably unused
- compatibility and rollback surfaces were preserved
- the remaining mess is localized and understood rather than hidden behind duplicate code paths

## Is the code likely to run more safely now?

Yes.

The current codebase is less likely to fail for the previously known reasons, and the active execution paths are less ambiguous. The biggest runtime improvement came from fixing the blockers first; the structural pass mainly reduced misleading or duplicate service-local code that would otherwise obscure ownership and maintenance.

## Recommended next step

The next step should be validation/runtime execution, not another cleanup pass.

Recommended order:
1. Run the local realtime-edge validation harness in full stack mode if Docker is available.
2. Review any runtime failures that remain after the service-only cleanup.
3. Only then decide whether another targeted structural pass is justified.

## Validation snapshot

Green in this pass:
- all scoped services compile
- realtime-edge focused test suite passes
- focused auth and user tests pass
- friendship, presence, and notification test sources compile

Known outstanding validation gap:
- chat-service compileTestJava is blocked by stale contract tests that reference old common APIs not present in the current codebase. That issue remains outside this cleanup scope.
