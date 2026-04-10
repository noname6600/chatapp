## Why

Users cannot reliably start a private chat with another user, which blocks core one-to-one communication and causes dead-end interaction from friend or profile entry points. This needs immediate correction because private chat initiation is a primary chat workflow and current failures reduce trust in the messaging experience.

## What Changes

- Define and implement a stable private chat initiation flow that either opens an existing direct room or creates one when absent.
- Ensure the initiation request validates target user eligibility (not self, valid user, and permitted relationship rules) and returns clear error outcomes.
- Align frontend behavior so user actions from friend/profile contexts produce deterministic navigation into the private chat conversation on success.
- Add error-state handling for initiation failures so users receive actionable feedback instead of silent failure.
- Add regression coverage for backend initiation logic and frontend initiation/navigation behavior.

## Capabilities

### New Capabilities
- `private-chat-initiation-flow`: Covers starting a private chat from user-facing entry points, including create-or-open semantics, response handling, and navigation to the target conversation.

### Modified Capabilities
- `fe-rest-api-gateway-integration`: Clarifies frontend request/response handling expectations for private chat initiation endpoint failures and success payload requirements.

## Impact

- Backend services handling private/direct room lookup and creation (chat-related service and possible friendship/user validation paths).
- Frontend chat entry-point components and routing behavior for direct message navigation.
- API contracts between frontend and gateway/chat service for private chat initiation responses.
- Test suites for integration and UI behavior around private chat creation/opening flows.
