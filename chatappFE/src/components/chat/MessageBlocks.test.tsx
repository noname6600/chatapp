// @vitest-environment jsdom

import { cleanup, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"

import MessageBlocks from "./MessageBlocks"

vi.mock("../user/Username", () => ({
  default: ({ userId, children }: { userId: string; children: React.ReactNode }) => (
    <span data-testid={`mention-user-${userId}`}>{children}</span>
  ),
}))

describe("MessageBlocks", () => {
  afterEach(() => {
    cleanup()
  })

  it("renders ordered text, link, and asset blocks", () => {
    render(
      <MessageBlocks
        blocks={[
          { type: "TEXT", text: "Before https://example.com/docs" },
          {
            type: "ASSET",
            attachment: {
              type: "IMAGE",
              url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg",
              fileName: "a.jpg",
            },
          },
          { type: "TEXT", text: "After" },
        ]}
      />
    )

    expect(screen.getByRole("link", { name: "https://example.com/docs" })).toBeTruthy()
    expect(screen.getByAltText("a.jpg")).toBeTruthy()
    expect(screen.getByText("After")).toBeTruthy()
  })

  it("renders mentions as @DisplayName and wires clickable mention wrapper", () => {
    render(
      <MessageBlocks
        blocks={[{ type: "TEXT", text: "Hi @alice" }]}
        resolveMentionLabel={(token) => (token === "alice" ? "Alice Nguyen" : token)}
        resolveMentionUserId={(token) => (token === "alice" ? "user-1" : null)}
      />
    )

    expect(screen.getByText("@Alice Nguyen")).toBeTruthy()
    expect(screen.getByTestId("mention-user-user-1")).toBeTruthy()
  })

  it("skips empty text blocks and keeps render spacing clean", () => {
    const { container } = render(
      <MessageBlocks
        blocks={[
          { type: "TEXT", text: "   " },
          {
            type: "ASSET",
            attachment: {
              type: "IMAGE",
              url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg",
              fileName: "a.jpg",
            },
          },
          { type: "TEXT", text: "Visible text" },
          { type: "TEXT", text: "" },
        ]}
      />
    )

    expect(container.querySelectorAll(".text-sm.text-gray-800")).toHaveLength(1)
    expect(screen.getAllByAltText("a.jpg")).toHaveLength(1)
    expect(screen.getByText("Visible text")).toBeTruthy()
  })

  it("returns no rendered output when all text blocks are empty", () => {
    const { container } = render(
      <MessageBlocks
        blocks={[
          { type: "TEXT", text: "" },
          { type: "TEXT", text: "   " },
        ]}
      />
    )

    expect(container.firstChild).toBeNull()
  })
})