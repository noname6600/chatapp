/* @vitest-environment jsdom */

import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import ForwardMessageModal from "./ForwardMessageModal"
import type { ChatMessage } from "../../types/message"
import type { Room } from "../../types/room"
import type { UserProfile } from "../../types/user"

function makeMessage(): ChatMessage {
  return {
    messageId: "source-1",
    roomId: "room-1",
    senderId: "user-1",
    seq: 10,
    type: "TEXT",
    content: "Original message",
    replyToMessageId: null,
    clientMessageId: null,
    createdAt: "2026-01-01T00:00:10.000Z",
    editedAt: null,
    deleted: false,
    attachments: [],
    reactions: [],
  }
}

function makeRoom(id: string, name: string, otherUserId?: string): Room {
  return {
    id,
    type: otherUserId ? "PRIVATE" : "GROUP",
    name,
    createdBy: "user-1",
    createdAt: "2026-01-01T00:00:00.000Z",
    myRole: "MEMBER",
    unreadCount: 0,
    otherUserId: otherUserId ?? null,
  }
}

describe("ForwardMessageModal", () => {
  it("filters rooms by name and @username and confirms forwarding", async () => {
    const onConfirm = vi.fn(async () => {})

    const users: Record<string, UserProfile> = {
      "user-2": {
        accountId: "user-2",
        username: "alice",
        displayName: "Alice Nguyen",
        avatarUrl: null,
        aboutMe: null,
        backgroundColor: null,
      },
    }

    render(
      <ForwardMessageModal
        open
        sourceMessage={makeMessage()}
        currentRoomId="room-1"
        rooms={[
          makeRoom("room-1", "Current"),
          makeRoom("room-2", "Engineering"),
          makeRoom("room-3", "Direct Alice", "user-2"),
        ]}
        users={users}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />
    )

    // current room must be excluded
    expect(screen.queryByText("Current")).toBeNull()
    expect(screen.getByText("Engineering")).toBeTruthy()
    expect(screen.getByText("Direct Alice")).toBeTruthy()

    fireEvent.change(screen.getByPlaceholderText("Search groups or @username"), {
      target: { value: "@alice" },
    })

    expect(screen.queryByText("Engineering")).toBeNull()
    expect(screen.getByText("Direct Alice")).toBeTruthy()

    fireEvent.click(screen.getByText("Direct Alice"))
    fireEvent.click(screen.getByRole("button", { name: "Forward" }))

    await waitFor(() => {
      expect(onConfirm).toHaveBeenCalledWith("room-3")
    })
  })
})
