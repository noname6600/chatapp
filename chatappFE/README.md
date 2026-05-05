# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

## Message Editing Behavior

- Single text block edits show the `edited` timestamp inline with message text.
- Multi-block edits (text + media, or multiple blocks) show the `edited` timestamp on a new line below blocks.
- Empty text blocks are skipped during render to avoid unwanted spacing artifacts.
- Edit mode supports `@mention` autocomplete and token insertion, matching composer behavior.

### Related Components

- `src/components/chat/EditedIndicator.tsx`
- `src/components/chat/MessageItem.tsx`
- `src/components/chat/MessageBlocks.tsx`

## Friendship Realtime Events

The frontend listens to friendship websocket messages from the friendship service and updates friend-request UI state in realtime.

Supported friendship websocket message types:

- `friendship.request.received`: increments the unread friend-request badge and inserts the requester into the pending list.
- `friendship.request.accepted`: reconciles badge state and promotes the relationship to `FRIENDS` in the UI.
- `friendship.request.declined`: removes the pending request from local UI state.
- `friendship.request.cancelled`: removes the cancelled request from local UI state.
- `friendship.status.changed`: reconciles friendship status changes such as unfriend/block flows.

Related files:

- `src/websocket/friendship.socket.ts`
- `src/store/friendship.provider.tsx`
- `src/components/FriendRequestBadge.tsx`
- `src/pages/FriendsPage.tsx`

## Deployment Environment Guide

Frontend runtime is designed to work behind gateway ingress and should use environment-based URLs.

### Local Mode Defaults

Use `chatappFE/.env.local`:

- `VITE_API_URL=http://localhost:8080/api/v1`
- `VITE_WS_URL=ws://localhost:8080`

This routes REST and WebSocket traffic through gateway in local mode.

### VPS Production Defaults

Use `chatappFE/.env.production`:

- `VITE_API_URL=https://api.chatweb.nani.id.vn/api/v1`
- `VITE_WS_URL=wss://api.chatweb.nani.id.vn`

Domain mapping:

- Frontend domain: `https://chatweb.nani.id.vn`
- API and WebSocket ingress: `https://api.chatweb.nani.id.vn`

### One-Command VPS Startup

The full stack (backend, frontend, nginx) is started from backend compose:

```bash
cd chatappBE
docker compose --env-file .env.production -f docker-compose.yml up -d --build
```

See `chatappBE/DEPLOY.md` for preflight, verification, troubleshooting, and rollback.
