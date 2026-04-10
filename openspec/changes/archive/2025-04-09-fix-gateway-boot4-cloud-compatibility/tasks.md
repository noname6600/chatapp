## 1. Dependency Compatibility Alignment

- [x] 1.1 Reproduce gateway local startup failure with `gradlew.bat :gateway-service:bootRun --args=--spring.profiles.active=local` and capture failing auto-configuration/class references
- [x] 1.2 Align gateway Spring Boot and Spring Cloud dependency versions (or managed artifacts) to a compatible set for reactive gateway startup
- [x] 1.3 Keep compatibility changes minimal and scoped to `gateway-service` build/runtime configuration

## 2. Gateway Startup Stabilization

- [x] 2.1 Remove or reduce brittle autoconfiguration exclusions after dependency alignment, keeping only strictly necessary guards
- [x] 2.2 Verify gateway-service starts successfully in local profile and binds to port 8080
- [ ] 2.3 Verify auth-service plus gateway-service startup baseline (ports 8081 and 8080) used by launcher verification path

## 3. Validation and Regression Safety

- [x] 3.1 Run targeted gateway tests and checks for security/cors routing behavior after compatibility changes
- [x] 3.2 Re-run local gateway health check (`/actuator/health`) and confirm successful response
- [x] 3.3 Confirm startup failure signature no longer reports class-resolution bootstrap errors seen before

## 4. Runbook and Handoff

- [x] 4.1 Update `chatappBE/LOCAL_HYBRID_RUNBOOK.txt` troubleshooting to distinguish command syntax issues from gateway compatibility failures
- [x] 4.2 Document exact verification commands and expected outputs for auth+gateway local startup
- [ ] 4.3 Resume `add-run-all-services-local-script` change and complete its pending verification tasks once gateway compatibility is fixed
