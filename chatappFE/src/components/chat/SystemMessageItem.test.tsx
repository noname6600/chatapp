/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi, beforeEach } from "vitest"

import SystemMessageItem from "./SystemMessageItem"
import { groupMessages } from "./messageGrouping"
import type { ChatMessage } from "../../types/message"

const mocks = vi.hoisted(() => ({
  openOverlay: vi.fn(),
}))

let usersState: Record<string, { accountId: string; username: string; displayName: string }> = {}

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: { users: typeof usersState }) => unknown) =>
    selector({ users: usersState }),
}))

vi.mock("../../store/userOverlay.store", () => ({
  useUserOverlay: (selector: (state: { open: typeof mocks.openOverlay }) => unknown) =>
    selector({ open: mocks.openOverlay }),
}))

function makeSystemMessage(overrides?: Partial<ChatMessage>): ChatMessage {
  return {
    messageId: "system-1",
    roomId: "room-1",
    senderId: "system",
    seq: 100,
    type: "SYSTEM",
    content: "",
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: "2026-01-01T00:00:00.000Z",
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
    actorUserId: "user-1",
    systemEventType: "PIN",
    targetMessageId: "target-1",
    ...overrides,
  }
}

function makeTextMessage(id: string, seq: number, senderId: string): ChatMessage {
  return {
    messageId: id,
    roomId: "room-1",
    senderId,
    seq,
    type: "TEXT",
    content: `message ${seq}`,
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: `2026-01-01T00:00:${String(seq).padStart(2, "0")}.000Z`,
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
  }
}

describe("SystemMessageItem", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    usersState = {
      "user-1": {
        accountId: "user-1",
        username: "alice",
        displayName: "Alice",
      },
    }
  })

  it("renders pin row and triggers profile/jump/open-pins actions", () => {
    const onJumpToMessage = vi.fn()
    const onOpenPinnedMessages = vi.fn()

    render(
      <SystemMessageItem
        message={makeSystemMessage()}
        onJumpToMessage={onJumpToMessage}
        onOpenPinnedMessages={onOpenPinnedMessages}
      />
    )

    fireEvent.click(screen.getByText("Alice"))
    expect(mocks.openOverlay).toHaveBeenCalled()

    fireEvent.click(screen.getByRole("button", { name: "a message" }))
    expect(onJumpToMessage).toHaveBeenCalledWith("target-1")

    fireEvent.click(screen.getByRole("button", { name: "See all pinned messages" }))
    expect(onOpenPinnedMessages).toHaveBeenCalledTimes(1)
  })

  it("uses current actor display name after rename", () => {
    const message = makeSystemMessage({ systemEventType: "JOIN", targetMessageId: null })
    const { rerender } = render(
      <SystemMessageItem
        message={message}
        onJumpToMessage={vi.fn()}
        onOpenPinnedMessages={vi.fn()}
      />
    )

    expect(screen.getByText("Alice")).toBeTruthy()

    usersState = {
      ...usersState,
      "user-1": {
        ...usersState["user-1"],
        displayName: "Alice Renamed",
      },
    }

    rerender(
      <SystemMessageItem
        message={message}
        onJumpToMessage={vi.fn()}
        onOpenPinnedMessages={vi.fn()}
      />
    )

    expect(screen.getByText("Alice Renamed")).toBeTruthy()
  })

  it("treats SYSTEM messages as grouping separators", () => {
    const grouped = groupMessages([
      makeTextMessage("m1", 1, "user-1"),
      makeTextMessage("m2", 2, "user-1"),
      makeSystemMessage({ messageId: "sys", seq: 3 }),
      makeTextMessage("m3", 4, "user-1"),
    ])

    expect(grouped).toHaveLength(3)
    expect(grouped[0].messages.map((m) => m.messageId)).toEqual(["m1", "m2"])
    expect(grouped[1].messages.map((m) => m.messageId)).toEqual(["sys"])
    expect(grouped[2].messages.map((m) => m.messageId)).toEqual(["m3"])
  })
})
