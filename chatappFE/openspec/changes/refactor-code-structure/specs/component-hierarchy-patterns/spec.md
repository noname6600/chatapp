## ADDED Requirements

### Requirement: Component classification pattern
All components SHALL be classified as either Presentational (UI-focused) or Container (logic-focused). This classification SHALL guide folder organization and responsibilities.

#### Scenario: Presentational components
- **WHEN** a component accepts all data and callbacks via props
- **THEN** the component SHALL be classified as Presentational
- **THEN** it SHALL not fetch data or manage local state beyond inline UI state
- **THEN** it SHALL go in `features/<feature>/components/`

#### Scenario: Container components
- **WHEN** a component fetches data, manages state, or orchestrates other components
- **THEN** the component SHALL be classified as Container
- **THEN** it SHALL use hooks to fetch data and manage state
- **THEN** it SHALL delegate rendering to Presentational components
- **THEN** it SHALL go in `features/<feature>/components/` with clear naming (e.g., `<Domain>Container.tsx`)

### Requirement: Data fetching through hooks
Components SHALL NOT directly call API functions. All data fetching SHALL happen through custom hooks using React Query.

#### Scenario: React Query integration
- **WHEN** a component needs to fetch data
- **THEN** a custom hook using `useQuery` or `useMutation` SHALL be created
- **THEN** the hook SHALL be in `features/<feature>/hooks/`
- **THEN** the hook SHALL handle loading, error, and success states
- **THEN** the component uses the hook to get data and status

#### Scenario: Hook naming convention
- **WHEN** creating a custom hook
- **THEN** hook name SHALL start with `use` (e.g., `useFetchChatMessages`, `usePostMessage`)
- **THEN** hook name SHALL reflect what it does, not where it gets data from

### Requirement: Component composition structure
Components SHALL be composed in a clear hierarchy where parent components combine Presentational and Container components with clear data flow.

#### Scenario: Unidirectional data flow
- **WHEN** composing components
- **THEN** data SHALL flow from containers down to presentational components via props
- **THEN** callbacks and event handlers SHALL flow upward via props
- **THEN** business logic SHALL live in containers, not in presentational components

#### Scenario: Component file organization
- **WHEN** organizing components within a feature
- **THEN** related presentational components MAY be grouped in subfolders (e.g., `MessageList/`, `MessageItem/`)
- **THEN** Container components SHALL be at the feature level clearly named as containers

### Requirement: No business logic in Presentational components
Presentational components SHALL contain only UI rendering logic. All business decisions SHALL be made in Containers or Hooks.

#### Scenario: Formatting and display
- **WHEN** a presentational component needs to format data for display
- **THEN** formatting logic MAY be inline (e.g., date formatting with `dayjs`)
- **THEN** complex transformations SHALL happen in hook or service layer, not in component

#### Scenario: Conditional rendering
- **WHEN** a presentational component needs to conditionally render UI
- **THEN** the condition parameter SHALL be passed via props
- **THEN** the component SHALL NOT fetch data to determine condition

### Requirement: Shared component organization
Reusable UI components that are not specific to any feature SHALL be in `/src/components/shared/`.

#### Scenario: Identifying shared components
- **WHEN** a component is used by multiple features
- **THEN** it SHALL be moved to `/src/components/shared/`
- **THEN** features import from shared, not from other features' components
