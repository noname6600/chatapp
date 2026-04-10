## 1. PowerShell Script

- [x] 1.1 Create `chatappBE/scripts/start-services-local.ps1` with named switch parameters (`-All`, `-Auth`, `-User`, `-Chat`, `-Presence`, `-Friendship`, `-Notification`, `-Upload`, `-Gateway`) defaulting to `-All` when no switch is provided
- [x] 1.2 Implement per-service `Start-Process powershell` launch with window title set to service name and working directory set to `chatappBE/`
- [x] 1.3 Add Docker infra reminder printed to the launching terminal before any windows open
- [ ] 1.4 Verify script launches auth-service and gateway-service correctly in separate windows

## 2. Batch Wrapper

- [x] 2.1 Create `chatappBE/scripts/start-services-local.bat` that delegates to `start-services-local.ps1` via `powershell -ExecutionPolicy Bypass -File`
- [x] 2.2 Verify batch file launches services correctly from a cmd prompt

## 3. Runbook Update

- [x] 3.1 Update `chatappBE/LOCAL_HYBRID_RUNBOOK.txt` to add a "Multi-Service Startup" section documenting `.\scripts\start-services-local.ps1` and `scripts\start-services-local.bat`
- [x] 3.2 Add a note in the runbook that Docker infra must be running before using the scripts

## 4. Verification

- [ ] 4.1 Start Docker infra with `docker-compose.local.yml` and run the PS script with `-Auth -Gateway` — confirm two windows open and services start on ports 8081 and 8080
- [ ] 4.2 Confirm both services bind their ports successfully (no placeholder errors, no port conflicts)
- [ ] 4.3 Stop services and clean up Docker infra
