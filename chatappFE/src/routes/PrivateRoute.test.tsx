/* @vitest-environment jsdom */

import { describe, expect, it, vi } from "vitest"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { render, screen } from "@testing-library/react"
import PrivateRoute from "./PrivateRoute"

const useAuthMock = vi.hoisted(() => vi.fn())

vi.mock("../hooks/useAuth", () => ({
  useAuth: () => useAuthMock(),
}))

describe("PrivateRoute", () => {
  it("keeps protected content blocked while bootstrap is running", () => {
    useAuthMock.mockReturnValue({
      accessToken: "token",
      isInitializing: false,
      isBootstrapping: true,
    })

    render(
      <MemoryRouter initialEntries={["/chat"]}>
        <Routes>
          <Route element={<PrivateRoute />}>
            <Route path="/chat" element={<div>Protected Chat</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Loading...")).toBeTruthy()
    expect(screen.queryByText("Protected Chat")).toBeNull()
  })

  it("renders protected content once bootstrap is complete", () => {
    useAuthMock.mockReturnValue({
      accessToken: "token",
      isInitializing: false,
      isBootstrapping: false,
    })

    render(
      <MemoryRouter initialEntries={["/chat"]}>
        <Routes>
          <Route element={<PrivateRoute />}>
            <Route path="/chat" element={<div>Protected Chat</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText("Protected Chat")).toBeTruthy()
  })
})
