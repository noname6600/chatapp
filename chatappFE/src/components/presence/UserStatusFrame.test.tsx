// @vitest-environment jsdom

import { describe, expect, it } from "vitest"
import { render } from "@testing-library/react"

import UserStatusFrame from "./UserStatusFrame"

describe("UserStatusFrame", () => {
  it("applies online ring styling", () => {
    const { container } = render(
      <UserStatusFrame status="ONLINE" size={32}>
        <img alt="avatar" src="/default-avatar.png" />
      </UserStatusFrame>
    )

    const frame = container.querySelector("img")?.nextElementSibling as HTMLElement
    expect(frame.className.includes("bg-green-500")).toBe(true)
  })

  it("applies away ring styling", () => {
    const { container } = render(
      <UserStatusFrame status="AWAY" size={32}>
        <img alt="avatar" src="/default-avatar.png" />
      </UserStatusFrame>
    )

    const frame = container.querySelector("img")?.nextElementSibling as HTMLElement
    expect(frame.className.includes("bg-amber-400")).toBe(true)
  })

  it("applies offline ring styling", () => {
    const { container } = render(
      <UserStatusFrame status="OFFLINE" size={32}>
        <img alt="avatar" src="/default-avatar.png" />
      </UserStatusFrame>
    )

    const frame = container.querySelector("img")?.nextElementSibling as HTMLElement
    expect(frame.className.includes("bg-gray-500")).toBe(true)
  })

  it("scales dot size with avatar size", () => {
    const small = render(
      <UserStatusFrame status="ONLINE" size={20}>
        <img alt="small" src="/default-avatar.png" />
      </UserStatusFrame>
    )

    const large = render(
      <UserStatusFrame status="ONLINE" size={80}>
        <img alt="large" src="/default-avatar.png" />
      </UserStatusFrame>
    )

    const smallFrame = small.container.querySelector("img")?.nextElementSibling as HTMLElement
    const largeFrame = large.container.querySelector("img")?.nextElementSibling as HTMLElement

    const smallSize = Number.parseInt(smallFrame.style.width, 10)
    const largeSize = Number.parseInt(largeFrame.style.width, 10)

    expect(largeSize).toBeGreaterThan(smallSize)
  })
})
