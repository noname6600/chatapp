// @vitest-environment jsdom

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import RoomHeader from "./RoomHeader"

describe("RoomHeader invite actions", () => {
  it("keeps only Invite Members entry and triggers invite callback", () => {
    const onInvite = vi.fn()

    render(
      <RoomHeader
        room={{
          id: "group-1",
          type: "GROUP",
          name: "Design Guild",
          avatarUrl: null,
          createdBy: "me",
          createdAt: "2024-01-01T00:00:00.000Z",
          myRole: "OWNER",
          unreadCount: 0,
        }}
        onInvite={onInvite}
      />
    )

    fireEvent.click(screen.getByRole("button", { name: /Design Guild/i }))

    const inviteButton = screen.getByRole("button", { name: "Invite Members" })
    fireEvent.click(inviteButton)

    expect(onInvite).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole("button", { name: "Send Invite Card" })).toBeNull()
  })
})
