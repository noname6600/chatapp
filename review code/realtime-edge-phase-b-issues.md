# Realtime Edge Phase B - Open Issues

**Status:** Known issues and rollout blockers
**Date:** May 12, 2026
**Scope:** Notification migration only

---

## 1. Blockers Remaining

- `:notification-service:test` is blocked by unrelated pre-existing compile failures in older test sources, so the new Phase B controller test cannot be used as a clean Gradle validation gate.
- The edge session registry remains in-memory, so a second edge instance would not see the first instance's session ownership.
- No real staging soak has been run yet against a deployed stack with live Redis and notification-service traffic.
- Production canary approval should wait until the notification endpoint and edge fanout path are exercised in a deployed staging environment.

---

## 2. Validation Noise

- Edge test runs still emit Kafka connection warnings because listeners try to contact `localhost:9092` when no broker is available in the local test environment.
- Those warnings do not fail the focused notification tests, but they do make the logs noisier than ideal.

---

## 3. Risk Notes

- The notification command contract is intentionally narrow, which is good for rollout safety, but it should be revisited if more notification actions are added later.
- The current edge path depends on the existing JWT handshake and token-forwarding flow.
- Multi-session fanout is verified only in-process, not across multiple edge nodes.

---

## 4. Current Assessment

- Edge notification routing: validated locally
- Edge notification delivery: validated locally
- Load smoke: validated locally
- Notification-service runtime endpoint: not yet validated in a deployed staging environment
- Production canary: not recommended yet
