## 1. Gateway CORS Filter Ordering

- [x] 1.1 Add `import org.springframework.core.Ordered` and `import org.springframework.core.annotation.Order` to `GatewayConfig.java`
- [x] 1.2 Annotate the `corsWebFilter` bean method with `@Order(Ordered.HIGHEST_PRECEDENCE)` in `GatewayConfig.java`
- [x] 1.3 Verify the gateway compiles cleanly (`./gradlew :gateway-service:compileJava`)

## 2. Frontend Interceptor Guard

- [x] 2.1 Add `shouldRefresh` option to `createAuthRefreshInterceptor` call in `base.api.ts` that returns `false` when `error?.config?.url` includes `/login` or `/register`

## 3. Frontend Error Message Extraction

- [x] 3.1 Add `import axios from "axios"` to `error.ts` (if not already present)
- [x] 3.2 Update `extractErrorMessage` in `error.ts` to check `axios.isAxiosError(error)` first and return `error.response?.data?.message ?? error.message`

## 4. Verification

- [x] 4.1 Run the local stack and confirm no CORS errors on authenticated route preflight (e.g. `OPTIONS /api/v1/users/me` from `http://localhost:5173`)
- [x] 4.2 Attempt login with wrong credentials and confirm the UI shows the backend message (e.g. "Invalid credentials"), not "No refresh token"
