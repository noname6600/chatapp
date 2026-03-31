## Rollback Note: fix-reaction-ownership-be

If emergency rollback is required after deployment:

1. Revert backend commit(s) introducing ownership-aware reaction projection/query.
2. Revert `ReactionResponse` additive field `reactedByMe` mapping changes.
3. Redeploy chat-service.
4. Validate `/api/v1/messages/latest` and `/api/v1/messages/before` still return previous reaction shape (`emoji`, `count`).
5. Confirm realtime reaction toggle still functions with existing frontend dedup/reconcile logic.

Expected rollback behavior:
- Frontend continues to function due to defensive normalization and fallback handling.
- Initial ownership highlight may be less accurate until realtime updates arrive.
