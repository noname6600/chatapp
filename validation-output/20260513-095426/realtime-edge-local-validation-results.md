# Realtime Edge Local Validation Results

- Run ID: 20260513-095426
- Mode: test
- Started: 2026-05-13T09:54:26.3315016+07:00
- Completed: 2026-05-13T09:57:13.7195676+07:00
- Overall Status: FAIL
- Recommendation: **stop and investigate**

## Entry Gate Results
| Gate | Status | Duration(s) | Exit Code |
|---|---|---:|---:|
| multi-domain-compile | PASS | 34.44 | 0 |
| friendship-compileTest | PASS | 27.39 | 0 |
| focused-migration-tests | PASS | 41.51 | 0 |
| full-edge-test-suite | FAIL | 51.69 | 1 |

## Environment Startup Status
- Compose status: NOT-TESTED

### Containers
| Container | Running | Health |
|---|---|---|
| (not checked) | - | - |

### Endpoints
| Service | URL | OK | HTTP | Latency(ms) |
|---|---|---|---:|---:|
| (not checked) | - | - | - | - |

## Automated Test Results
| Test Group | Status | Duration(s) | Exit Code |
|---|---|---:|---:|
| (not run) | NOT-TESTED | - | - |

## Scripted Runtime Scenarios
| Scenario | Status | Latency(ms) | Details |
|---|---|---:|---|
| (not run) | NOT-TESTED | - | - |

## Issue Classification
- Critical: 2
- High: 0
- Medium: 0
- Low: 0

| Severity | Area | Message |
|---|---|---|
| critical | entry-gates | Entry gate failed: full-edge-test-suite |
| critical | test | Entry gate failed: full-edge-test-suite |

## Artifacts
- Output directory: D:\Work\PET\chatappPET\chatapp\validation-output\20260513-095426
- Logs directory: D:\Work\PET\chatappPET\chatapp\validation-output\20260513-095426\logs
- JSON report: D:\Work\PET\chatappPET\chatapp\review code\realtime-edge-local-validation-results.json
- Markdown report: D:\Work\PET\chatappPET\chatapp\review code\realtime-edge-local-validation-results.md
