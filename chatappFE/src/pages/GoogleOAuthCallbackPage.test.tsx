/* @vitest-environment jsdom */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { cleanup, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"

import GoogleOAuthCallbackPage from "./GoogleOAuthCallbackPage"

const authServiceMocks = vi.hoisted(() => ({
  exchangeGoogleOAuthCodeApi: vi.fn(),
}))

const loginMock = vi.hoisted(() => vi.fn())
const navigateMock = vi.hoisted(() => vi.fn())

vi.mock("../api/auth.service", () => ({
  exchangeGoogleOAuthCodeApi: authServiceMocks.exchangeGoogleOAuthCodeApi,
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

const renderPage = (initialEntry: string) =>
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <GoogleOAuthCallbackPage />
    </MemoryRouter>
  )

describe("GoogleOAuthCallbackPage", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it("exchanges code, bootstraps auth, and navigates to chat on success", async () => {
    authServiceMocks.exchangeGoogleOAuthCodeApi.mockResolvedValue({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      accessTokenExpiresIn: 900,
    })
    loginMock.mockResolvedValue(undefined)

    renderPage("/auth/oauth/google/callback?code=handoff-code")

    await waitFor(() => {
      expect(authServiceMocks.exchangeGoogleOAuthCodeApi).toHaveBeenCalledWith("handoff-code")
    })
    expect(loginMock).toHaveBeenCalledWith("access-token", "refresh-token")

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith("/chat", { replace: true })
    })
  })

  it("shows an error when the callback is missing a handoff code", async () => {
    renderPage("/auth/oauth/google/callback")

    await waitFor(() => {
      expect(screen.getByText("Google login could not be completed. Please try again.")).toBeTruthy()
    })
    expect(authServiceMocks.exchangeGoogleOAuthCodeApi).not.toHaveBeenCalled()
  })

  it("shows the backend error when exchange fails", async () => {
    authServiceMocks.exchangeGoogleOAuthCodeApi.mockRejectedValue(new Error("OAuth login code is invalid or expired"))

    renderPage("/auth/oauth/google/callback?code=used-code")

    await waitFor(() => {
      expect(screen.getByText("OAuth login code is invalid or expired")).toBeTruthy()
    })
    expect(loginMock).not.toHaveBeenCalled()
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it("shows oauth error query state without attempting exchange", async () => {
    renderPage("/auth/oauth/google/callback?oauth_error=google_login_failed")

    await waitFor(() => {
      expect(screen.getByText("Google login failed. Please try again.")).toBeTruthy()
    })
    expect(authServiceMocks.exchangeGoogleOAuthCodeApi).not.toHaveBeenCalled()
  })
})