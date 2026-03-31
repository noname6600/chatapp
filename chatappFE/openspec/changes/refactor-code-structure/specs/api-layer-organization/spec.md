## ADDED Requirements

### Requirement: Three-layer API architecture
Each feature's API logic SHALL be organized in three layers: HTTP client (`*.api.ts`), business logic service (`*.service.ts`), and React Query hooks. This separation enables independent testing, clear api contracts, and centralized data transformations.

#### Scenario: API layer file organization
- **WHEN** organizing API code for a feature
- **THEN** code SHALL be structured as: `features/<feature>/api/<entity>.api.ts` (HTTP calls), `features/<feature>/api/<entity>.service.ts` (business logic)

#### Scenario: HTTP client responsibilities
- **WHEN** implementing a `.api.ts` file
- **THEN** it SHALL contain only low-level HTTP client functions that directly call backend endpoints
- **THEN** each function SHALL map directly to a single backend API endpoint
- **THEN** functions SHALL return raw response data without business logic transformation

#### Scenario: Service layer responsibilities
- **WHEN** implementing a `.service.ts` file
- **THEN** it SHALL contain business logic, data validation, and transformation
- **THEN** it SHALL call `.api.ts` functions to fetch data
- **THEN** it SHALL handle errors, apply business rules, and format data for components

#### Scenario: React Query hooks
- **WHEN** exposing API data to React components
- **THEN** custom hooks in `features/<feature>/hooks/` SHALL use React Query (useQuery, useMutation)
- **THEN** hooks SHALL call service layer functions, not `.api.ts` functions directly
- **THEN** hooks SHALL integrate with Zustand store for state synchronization

### Requirement: API Request/Response typing
All API functions SHALL have explicit TypeScript types for request parameters and response data. Types SHALL be defined in the feature's `/types` folder.

#### Scenario: Typed API endpoints
- **WHEN** defining an API function like `fetchChatMessages()`
- **THEN** request parameters and response SHALL be typed explicitly
- **THEN** types SHALL be defined in `/features/<feature>/types/` and re-exported via `index.ts`

#### Scenario: Error handling standardization
- **WHEN** an API call fails
- **THEN** errors SHALL be caught and type-narrowed to distinguish between API errors, network errors, and validation errors
- **THEN** error information SHALL be passed to the service layer for consistent error handling
