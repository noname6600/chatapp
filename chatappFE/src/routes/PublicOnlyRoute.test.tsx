/* @vitest-environment jsdom */

import { describe, expect, it, vi } from "vitest"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { render, screen } from "@testing-library/react"
import PublicOnlyRoute from "./PublicOnlyRoute"

const useAuthMock = vi.hoisted(() => vi.fn())

vi.mock("../hooks/useAuth", () => ({
  useAuth: () => useAuthMock(),
}))

describe("PublicOnlyRoute", () => {
  it("shows loading while auth is initializing", () => {
    useAuthMock.mockReturnValue({
      accessToken: null,
      isInitializing: true,
      isBootstrapping: false,
    })

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<div>Auth Page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Loading...")).toBeTruthy()
    expect(screen.queryByText("Auth Page")).toBeNull()
  })

  it("redirects authenticated users away from login", () => {
    useAuthMock.mockReturnValue({
      accessToken: "token",
      isInitializing: false,
      isBootstrapping: false,
    })

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<div>Auth Page</div>} />
          </Route>
          <Route path="/chat" element={<div>Chat Page</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Chat Page")).toBeTruthy()
    expect(screen.queryByText("Auth Page")).toBeNull()
  })

  it("allows unauthenticated users to open login", () => {
    useAuthMock.mockReturnValue({
      accessToken: null,
      isInitializing: false,
      isBootstrapping: false,
    })

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <Routes>
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<div>Auth Page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Auth Page")).toBeTruthy()
  })
})