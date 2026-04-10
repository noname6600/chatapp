## 1. Author Hybrid Local Runbook

- [x] 1.1 Create `chatappBE/LOCAL_HYBRID_RUNBOOK.txt` with local preflight and scope note that only infrastructure runs in Docker.
- [x] 1.2 Add infrastructure startup commands for `postgres`, `redis`, `zookeeper`, and `kafka` using `docker-compose.local.yml`.
- [x] 1.3 Add infrastructure verification commands for PostgreSQL readiness, Redis ping, and Kafka reachability.

## 2. Document Native Service Startup Commands

- [x] 2.1 Add root-level Gradle `bootRun` commands for `auth-service`, `user-service`, `chat-service`, `presence-service`, `friendship-service`, `notification-service`, `upload-service`, and `gateway-service`.
- [x] 2.2 Document recommended startup sequence (auth first, dependency-bound services next, gateway last) and terminal layout guidance.
- [x] 2.3 Add stop/restart and cleanup commands to recover from stale ports or conflicting Docker app containers.

## 3. Integrate with Existing Deployment Runbook

- [x] 3.1 Update local section in `chatappBE/DEPLOY.md` to reference `chatappBE/LOCAL_HYBRID_RUNBOOK.txt` as the hybrid local mode guide.
- [x] 3.2 Ensure local vs VPS terminology remains consistent after adding the hybrid-mode reference.

## 4. Validate Documentation Accuracy

- [x] 4.1 Verify all documented service names and compose targets match `chatappBE/docker-compose.local.yml` and Gradle module paths in `chatappBE/settings.gradle`.
- [x] 4.2 Execute documented infrastructure commands and at least gateway health verification to confirm command correctness.
- [x] 4.3 Record any required `.env.local` caveats in the runbook so first-time local startup avoids avoidable failures.
