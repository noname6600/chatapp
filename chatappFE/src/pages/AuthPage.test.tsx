/* @vitest-environment jsdom */

import { describe, expect, it, vi, beforeEach, afterEach } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import AuthPage from "./AuthPage"

const authServiceMocks = vi.hoisted(() => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
}))

const loginMock = vi.hoisted(() => vi.fn())
const navigateMock = vi.hoisted(() => vi.fn())

vi.mock("../api/auth.service", () => ({
  loginApi: authServiceMocks.loginApi,
  registerApi: authServiceMocks.registerApi,
}))

vi.mock("../hooks/useAuth", () => ({
  useAuth: () => ({
    login: loginMock,
  }),
}))

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom")
  return {
    ...actual,
    useNavigate: () => navigateMock,
  }
})

const renderPage = () =>
  render(
    <MemoryRouter>
      <AuthPage />
    </MemoryRouter>
  )

describe("AuthPage", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it("shows backend wrong-credentials message on failed login", async () => {
    authServiceMocks.loginApi.mockRejectedValue(new Error("Invalid credentials"))

    renderPage()

    fireEvent.change(screen.getByPlaceholderText("Email"), {
      target: { value: "alice@example.com" },
    })
    fireEvent.change(screen.getByPlaceholderText("Password"), {
      target: { value: "WrongPass1!" },
    })

    fireEvent.click(screen.getByRole("button", { name: "Login" }))

    await waitFor(() => {
      expect(screen.getByText("Invalid credentials")).toBeTruthy()
    })
    expect(loginMock).not.toHaveBeenCalled()
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it("shows incomplete-account message when bootstrap fails after token issuance", async () => {
    authServiceMocks.loginApi.mockResolvedValue({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      accessTokenExpiresIn: 900,
    })
    loginMock.mockRejectedValue(
      new Error("Account setup incomplete. Please try again in a few seconds.")
    )

    renderPage()

    fireEvent.change(screen.getByPlaceholderText("Email"), {
      target: { value: "new@example.com" },
    })
    fireEvent.change(screen.getByPlaceholderText("Password"), {
      target: { value: "Password1!" },
    })

    fireEvent.click(screen.getByRole("button", { name: "Login" }))

    await waitFor(() => {
      expect(
        screen.getByText("Account setup incomplete. Please try again in a few seconds.")
      ).toBeTruthy()
    })
    expect(loginMock).toHaveBeenCalledWith("access-token", "refresh-token")
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it("navigates only after login bootstrap promise resolves", async () => {
    let resolveLogin: (() => void) | null = null

    authServiceMocks.loginApi.mockResolvedValue({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      accessTokenExpiresIn: 900,
    })
    loginMock.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveLogin = resolve
        })
    )

    renderPage()

    fireEvent.change(screen.getByPlaceholderText("Email"), {
      target: { value: "ok@example.com" },
    })
    fireEvent.change(screen.getByPlaceholderText("Password"), {
      target: { value: "Password1!" },
    })
    fireEvent.click(screen.getByRole("button", { name: "Login" }))

    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith("access-token", "refresh-token")
    })
    expect(navigateMock).not.toHaveBeenCalled()

    resolveLogin?.()

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/chat")
    })
  })

  it("disables auth actions and shows loading state while submitting", async () => {
    let resolveLoginApi: (() => void) | null = null

    authServiceMocks.loginApi.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveLoginApi = () =>
            resolve({
              accessToken: "access-token",
              refreshToken: "refresh-token",
              accessTokenExpiresIn: 900,
            })
        })
    )

    loginMock.mockResolvedValue(undefined)

    renderPage()

    fireEvent.change(screen.getByPlaceholderText("Email"), {
      target: { value: "loading@example.com" },
    })
    fireEvent.change(screen.getByPlaceholderText("Password"), {
      target: { value: "Password1!" },
    })

    fireEvent.click(screen.getByRole("button", { name: "Login" }))

    await waitFor(() => {
      const submitButton = screen.getByRole("button", { name: "Checking credentials..." }) as HTMLButtonElement
      expect(submitButton.disabled).toBe(true)
    })
    expect(screen.getByText("Verifying your credentials...")).toBeTruthy()
    const googleButton = screen.getByRole("button", { name: /continue with google/i }) as HTMLButtonElement
    expect(googleButton.disabled).toBe(true)

    resolveLoginApi?.()
  })

  it("shows deterministic fallback message when login fails without a message", async () => {
    authServiceMocks.loginApi.mockRejectedValue({})

    renderPage()

    fireEvent.change(screen.getByPlaceholderText("Email"), {
      target: { value: "alice@example.com" },
    })
    fireEvent.change(screen.getByPlaceholderText("Password"), {
      target: { value: "WrongPass1!" },
    })

    fireEvent.click(screen.getByRole("button", { name: "Login" }))

    await waitFor(() => {
      expect(
        screen.getByText("Login failed. Please verify your email and password and try again.")
      ).toBeTruthy()
    })
    expect(loginMock).not.toHaveBeenCalled()
    expect(navigateMock).not.toHaveBeenCalled()
  })
})
