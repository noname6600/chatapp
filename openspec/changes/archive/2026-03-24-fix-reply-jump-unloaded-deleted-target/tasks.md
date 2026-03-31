## 1. Reply Jump Resolver Flow

- [x] 1.1 Refactor reply click handler to use staged resolver (in-memory, around-query, bounded backfill, terminal fallback).
- [x] 1.2 Add resolver budget controls (max attempts and early stop on hasOlder=false).
- [x] 1.3 Ensure resolver dedupes concurrent clicks for the same target and room.

## 2. Paging and Scroll Stability

- [x] 2.1 Integrate bounded backfill with existing store pagination without breaking sequence ordering.
- [x] 2.2 Preserve viewport anchor during backfill and apply smooth scroll only when target enters DOM.
- [x] 2.3 Prevent room-top snap while resolver backfill is in progress.
- [x] 2.4 Ensure post-jump window supports both upward older-pagination and downward newer-pagination from landing context.

## 3. Deleted/Unavailable Target UX

- [x] 3.1 Add explicit unavailable/deleted reply-target state in preview rendering.
- [x] 3.2 Normalize empty and not-found lookup outcomes into one terminal fallback path.
- [x] 3.3 Keep UI interactive, preserve viewport position (no jump), and avoid repeated fetch loops after terminal fallback.

## 4. Validation

- [x] 4.1 Unit test staged resolver transitions and stop conditions.
- [x] 4.2 Integration test click-to-jump when target is unloaded but retrievable through older backfill.
- [x] 4.3 Integration test deleted/unavailable target fallback without crashes.
- [x] 4.4 Regression test that paging metadata and message ordering remain correct during resolver flow.
