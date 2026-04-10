// @vitest-environment jsdom

import { describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"

import ProfileSettingsPage from "./ProfileSettingsPage"

vi.mock("../store/auth.store", () => ({
  useAuth: () => ({
    currentUser: {
      accountId: "me",
      username: "john",
      displayName: "John",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    refreshCurrentUser: vi.fn(),
  }),
}))

vi.mock("../api/auth.service", () => ({
  changePasswordApi: vi.fn(),
  getEmailVerificationStatusApi: vi.fn().mockResolvedValue({
    email: "john@example.com",
    verified: false,
  }),
  sendVerificationEmailApi: vi.fn(),
}))

vi.mock("../components/profile/ProfileEditor", () => ({
  default: () => <div data-testid="profile-editor">Editor</div>,
}))

vi.mock("../components/profile/ProfilePreview", () => ({
  default: () => <div data-testid="profile-preview">Preview</div>,
}))

describe("ProfileSettingsPage", () => {
  it("renders profile and security tabs only", () => {
    render(
      <MemoryRouter>
        <ProfileSettingsPage />
      </MemoryRouter>
    )

    expect(screen.getByRole("button", { name: "Profile" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Security" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Account Recovery" })).toBeNull()
  })

  it("shows edit profile action by default and deterministic about fallback", () => {
    render(
      <MemoryRouter>
        <ProfileSettingsPage />
      </MemoryRouter>
    )

    expect(screen.getAllByRole("button", { name: "Edit Profile" }).length).toBeGreaterThan(0)
    expect(screen.getAllByTestId("settings-about-text")[0].textContent).toBe("No bio yet.")
  })
})
