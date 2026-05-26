# 12. Code Style and Patterns

## Strengths
- Clear package modularization by bounded context.
- Repeated use of interface-first service contracts.
- Consistent DTO usage at API boundaries.
- Pipeline pattern in chat send/edit/delete flow improves composability.
- Common response and exception model (`ApiResponse`, `BusinessException`).

## Architectural Patterns Observed
- Hexagonal-ish adapters in `realtime-edge-service` (`adapter/in`, `adapter/out`).
- Spring transactional application services in domain services.
- After-commit event publication for write->event consistency.
- Event envelope contract with metadata/correlation IDs.

## Naming and Consistency
Mostly good, but:
- some naming drift between folder and class names (`impl` vs direct service packages).
- mixed camel/snake naming conventions in JPA annotation column/index declarations.
- legacy comments mention migration phases; some class names still carry migration-era semantics.

## DTO / Entity Separation
Generally healthy:
- controllers receive request DTOs and return response DTOs.
- entities remain persistence-focused.

Areas to watch:
- payload conversion logic can grow in adapters; consider dedicated mapper modules where complexity rises.

## Code Smells / Anti-Patterns
1. Partial duplicate security configuration logic across all services.
2. Broad common module dependencies in most services (risk of transitive complexity).
3. Some consumers swallow exceptions with warn logs, potentially masking recurring delivery failures.
4. Runtime schema auto-update as a persistent pattern increases long-term maintenance risk.

## Under/Over Engineering Notes
- Over: multiple transport abstractions can feel heavy for small teams.
- Under: operational standardization (DLQ/idempotency/migrations) is less mature than code abstraction quality.

## Technical Debt Hotspots
- migration-phase residue around websocket split ownership.
- mixed realtime durability model (Kafka+Redis) with uneven guarantees.
- centralized gateway policy complexity without fully centralized policy tests.
