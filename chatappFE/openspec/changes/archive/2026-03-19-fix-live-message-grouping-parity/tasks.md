## 1. Reconciliation Logic

- [x] 1.1 Refactor socket receive flow in chat store to detect and replace matching optimistic message before normal upsert.
- [x] 1.2 Ensure replacement path performs canonical sort and exits without a second insert path.
- [x] 1.3 Add safeguards for unmatched server messages to continue through normal upsert behavior.

## 2. Grouping Behavior Alignment

- [x] 2.1 Simplify grouping predicates to use message data only (sender/time/attachments/optimistic markers) without transient local timers.
- [x] 2.2 Verify optimistic detection remains limited to temporary ids / MAX sequence and does not persist after reconciliation.
- [x] 2.3 Confirm live rendering and refresh rendering produce identical grouping boundaries for identical message sets.

## 3. Validation and Regression Checks

- [x] 3.1 Add targeted tests or reproducible manual checks for: immediate send grouping parity, duplicate prevention, and attachment boundary grouping.
- [x] 3.2 Run build and lint to confirm no regressions in TypeScript and bundling.
- [x] 3.3 Document verification steps in change notes for QA replay across direct and group rooms.

## Verification Notes

- Build: `npm run build` passes after parity fix.
- Lint: `npm run lint` reports pre-existing repository lint issues outside this change scope.
- QA replay steps:
	- Open a direct message room and send 3 text messages from same sender within 2 minutes.
	- Confirm live grouping equals grouping after refresh.
	- Send identical content quickly twice; confirm no duplicate optimistic+real entry appears for a single send.
	- Send an attachment between text messages; confirm attachment creates the same grouping boundary both live and after refresh.
	- Repeat in a group room with another sender to validate sender-boundary behavior.
