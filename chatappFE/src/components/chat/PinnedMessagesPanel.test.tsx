/* @vitest-environment jsdom */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import PinnedMessagesPanel from "./PinnedMessagesPanel"
import type { PinnedMessage } from "../../types/message"

function makePinnedMessage(overrides?: Partial<PinnedMessage>): PinnedMessage {
  return {
    messageId: "message-1",
    roomId: "room-1",
    senderId: "user-1",
    seq: 1,
    type: "TEXT",
    content: "Pinned hello",
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: "2026-01-01T00:00:01.000Z",
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
    ...overrides,
  }
}

describe("PinnedMessagesPanel", () => {
  it("does not render when closed", () => {
    const { container } = render(
      <PinnedMessagesPanel
        open={false}
        pinnedMessages={[]}
        onClose={vi.fn()}
        onJumpToMessage={vi.fn()}
        onRequestUnpin={vi.fn()}
      />
    )

    expect(container.firstChild).toBeNull()
  })

  it("renders pinned messages and triggers jump/unpin actions", () => {
    const onJumpToMessage = vi.fn()
    const onRequestUnpin = vi.fn()
    const pinned = makePinnedMessage()

    render(
      <PinnedMessagesPanel
        open
        pinnedMessages={[pinned]}
        onClose={vi.fn()}
        onJumpToMessage={onJumpToMessage}
        onRequestUnpin={onRequestUnpin}
      />
    )

    expect(screen.getByText("Pinned messages (1)")).toBeTruthy()
    expect(screen.getByText("Pinned hello")).toBeTruthy()

    fireEvent.click(screen.getByTitle("Jump to pinned message"))
    expect(onJumpToMessage).toHaveBeenCalledWith("message-1")

    fireEvent.click(screen.getByLabelText("Remove pin"))
    expect(onRequestUnpin).toHaveBeenCalledWith(pinned)
  })
})
