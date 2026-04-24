import { beforeEach, describe, expect, it, vi } from "vitest"

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  interceptorsRequestUse: vi.fn(),
  capturedShouldRefresh: undefined as ((error: unknown) => boolean) | undefined,
  createAuthRefreshInterceptor: vi.fn(),
}))

vi.mock("axios", () => ({
  default: {
    create: (...args: unknown[]) => mocks.create(...args),
  },
}))

vi.mock("axios-auth-refresh", () => ({
  default: (...args: unknown[]) => mocks.createAuthRefreshInterceptor(...args),
}))

vi.mock("./auth.service", () => ({
  refreshTokenApi: vi.fn(),
}))

describe("createBaseApi auth refresh behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.capturedShouldRefresh = undefined

    mocks.interceptorsRequestUse = vi.fn()

    mocks.create.mockReturnValue({
      interceptors: {
        request: {
          use: mocks.interceptorsRequestUse,
        },
      },
    })

    mocks.createAuthRefreshInterceptor.mockImplementation((_, __, options: { shouldRefresh: (error: unknown) => boolean }) => {
      mocks.capturedShouldRefresh = options.shouldRefresh
    })
  })

  it("does not refresh for login/register/refresh endpoint 401 responses", async () => {
    const { createBaseApi } = await import("./base.api")
    createBaseApi("http://localhost:8080/api")

    const shouldRefresh = mocks.capturedShouldRefresh
    expect(typeof shouldRefresh).toBe("function")

    expect(shouldRefresh?.({ config: { url: "/login" }, response: { data: {} } })).toBe(false)
    expect(shouldRefresh?.({ config: { url: "/register" }, response: { data: {} } })).toBe(false)
    expect(shouldRefresh?.({ config: { url: "/refresh" }, response: { data: {} } })).toBe(false)
  })

  it("does not refresh when backend signals incomplete account state", async () => {
    const { createBaseApi } = await import("./base.api")
    createBaseApi("http://localhost:8080/api")

    const shouldRefresh = mocks.capturedShouldRefresh
    expect(typeof shouldRefresh).toBe("function")

    expect(
      shouldRefresh?.({
        config: { url: "/auth/some-endpoint" },
        response: { data: { error: { code: "INCOMPLETE_ACCOUNT" } } },
      })
    ).toBe(false)
  })

  it("keeps refresh enabled for non-auth endpoint 401 responses", async () => {
    const { createBaseApi } = await import("./base.api")
    createBaseApi("http://localhost:8080/api")

    const shouldRefresh = mocks.capturedShouldRefresh
    expect(typeof shouldRefresh).toBe("function")

    expect(shouldRefresh?.({ config: { url: "/notifications/list" }, response: { data: {} } })).toBe(true)
  })
})
