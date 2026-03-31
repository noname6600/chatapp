/* @vitest-environment jsdom */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import PrivateRoomItem from "./PrivateRoomItem"

describe("PrivateRoomItem", () => {
  it("private chat room item shows badge when unreadCount > 0", () => {
    const onClick = vi.fn()

    render(
      <PrivateRoomItem
        room={{
          id: "room-private-1",
          type: "PRIVATE",
          name: "Alex",
          avatarUrl: null,
          createdBy: "me",
          createdAt: "2026-03-26T10:00:00.000Z",
          myRole: "MEMBER",
          unreadCount: 3,
          latestMessageAt: "2026-03-26T11:00:00.000Z",
          otherUserId: "user-2",
          lastMessage: {
            id: "msg-1",
            senderId: "user-2",
            senderName: "Alex",
            content: "Hello",
            createdAt: "2026-03-26T11:00:00.000Z",
          },
        }}
        isActive={false}
        onClick={onClick}
      />
    )

    expect(screen.getByText("3")).toBeTruthy()
    fireEvent.click(screen.getByText("Alex"))
    expect(onClick).toHaveBeenCalledTimes(1)
  })
})
