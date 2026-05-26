# Realtime Edge Local Validation Results

- Run ID: 20260513-095724
- Mode: full
- Started: 2026-05-13T09:57:24.2260348+07:00
- Completed: 2026-05-13T09:59:59.8702293+07:00
- Overall Status: FAIL
- Recommendation: **stop and investigate**

## Entry Gate Results
| Gate | Status | Duration(s) | Exit Code |
|---|---|---:|---:|
| multi-domain-compile | PASS | 33.41 | 0 |
| friendship-compileTest | PASS | 28.37 | 0 |
| focused-migration-tests | PASS | 35.49 | 0 |
| full-edge-test-suite | FAIL | 46.62 | 1 |

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
| critical | execution | Entry gate failed: full-edge-test-suite |

## Artifacts
- Output directory: D:\Work\PET\chatappPET\chatapp\validation-output\20260513-095724
- Logs directory: D:\Work\PET\chatappPET\chatapp\validation-output\20260513-095724\logs
- JSON report: D:\Work\PET\chatappPET\chatapp\review code\realtime-edge-local-validation-results.json
- Markdown report: D:\Work\PET\chatappPET\chatapp\review code\realtime-edge-local-validation-results.md
