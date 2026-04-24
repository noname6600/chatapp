// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { act, cleanup, render, screen, waitFor } from "@testing-library/react"

import RoomMembersSidebar from "./RoomMembersSidebar"
import { setFeatureFlag } from "../../config/featureFlags"
import { ChatEventType } from "../../constants/chatEvents"
import type { ChatSocketEvent } from "../../websocket/chat.socket"

const presenceState = {
  userStatuses: {
    owner: "ONLINE",
    "online-user": "ONLINE",
    "away-user": "AWAY",
    "offline-user": "OFFLINE",
    "new-user": "OFFLINE",
  },
  typingByRoom: {},
}

const userState = {
  users: {
    owner: {
      accountId: "owner",
      username: "owner",
      displayName: "Owner User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    "away-user": {
      accountId: "away-user",
      username: "away-user",
      displayName: "Away User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    "online-user": {
      accountId: "online-user",
      username: "online-user",
      displayName: "Online User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    "offline-user": {
      accountId: "offline-user",
      username: "offline-user",
      displayName: "Offline User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
    "new-user": {
      accountId: "new-user",
      username: "new-user",
      displayName: "New User",
      avatarUrl: null,
      aboutMe: null,
      backgroundColor: null,
    },
  },
  fetchUsers: vi.fn(async () => {}),
}

vi.mock("../../store/presence.store", () => ({
  usePresenceStore: (selector: (state: typeof presenceState) => unknown) => selector(presenceState),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: typeof userState) => unknown) => selector(userState),
}))

vi.mock("../../api/room.service", () => ({
  getRoomMembers: vi.fn(async () => [
    { userId: "owner", role: "OWNER" },
    { userId: "online-user", role: "MEMBER" },
    { userId: "away-user", role: "MEMBER" },
    { userId: "offline-user", role: "MEMBER" },
  ]),
}))

vi.mock("../user/UserAvatar", () => ({
  default: ({ userId }: { userId: string }) => <div data-testid={`avatar-${userId}`} />,
}))

vi.mock("../user/Username", () => ({
  default: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
}))

type ChatHandler = (event: ChatSocketEvent) => void
const chatHandlers: Array<{ handler: ChatHandler; unsub: ReturnType<typeof vi.fn> }> = []

vi.mock("../../websocket/chat.socket", () => ({
  onChatEvent: vi.fn((handler: ChatHandler) => {
    const unsub = vi.fn()
    chatHandlers.push({ handler, unsub })
    return unsub
  }),
}))

describe("RoomMembersSidebar", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    localStorage.setItem("my_user_id", "owner")
    localStorage.removeItem("feature_flag_enableRoomMemberStatusGrouping")
    setFeatureFlag("enableRoomMemberStatusGrouping", true)
    presenceState.userStatuses = {
      owner: "ONLINE",
      "online-user": "ONLINE",
      "away-user": "AWAY",
      "offline-user": "OFFLINE",
      "new-user": "OFFLINE",
    }
    chatHandlers.length = 0
  })

  it("renders room members grouped by rich presence status", async () => {
    render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByText("Owner (1)")).toBeTruthy()
      expect(screen.getByText("Online (2)")).toBeTruthy()
      expect(screen.getByText("Offline (1)")).toBeTruthy()
    })

    expect(screen.getByText("Owner User")).toBeTruthy()
    expect(screen.getByText("Online User")).toBeTruthy()
    expect(screen.getByText("Away User")).toBeTruthy()
    expect(screen.getByText("Offline User")).toBeTruthy()

    const onlineUserEl = screen.getByText("Online User")
    const awayUserEl = screen.getByText("Away User")
    expect(onlineUserEl.compareDocumentPosition(awayUserEl) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it("falls back to a single member list when grouping feature is disabled", async () => {
    setFeatureFlag("enableRoomMemberStatusGrouping", false)

    render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByText("Members (4)")).toBeTruthy()
    })
  })

  it("adds new member row when MEMBER_JOINED event arrives for current room", async () => {
    render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByTestId("avatar-owner")).toBeTruthy()
    })

    const { handler } = chatHandlers[chatHandlers.length - 1]

    act(() => {
      handler({
        type: ChatEventType.MEMBER_JOINED,
        payload: { roomId: "room-1", userId: "new-user", role: "MEMBER", joinedAt: "2026-04-20T00:00:00Z" },
      })
    })

    await waitFor(() => {
      expect(screen.getByTestId("avatar-new-user")).toBeTruthy()
    })

    expect(userState.fetchUsers).toHaveBeenCalledWith(["new-user"])
  })

  it("does not add duplicate row when MEMBER_JOINED arrives for already-present user", async () => {
    render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByTestId("avatar-owner")).toBeTruthy()
    })

    const { handler } = chatHandlers[chatHandlers.length - 1]

    act(() => {
      handler({
        type: ChatEventType.MEMBER_JOINED,
        payload: { roomId: "room-1", userId: "owner", role: "OWNER", joinedAt: "2026-04-20T00:00:00Z" },
      })
    })

    await waitFor(() => {
      expect(screen.getAllByTestId("avatar-owner")).toHaveLength(1)
    })
  })

  it("removes member row when MEMBER_LEFT event arrives for current room", async () => {
    render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByTestId("avatar-online-user")).toBeTruthy()
    })

    const { handler } = chatHandlers[chatHandlers.length - 1]

    act(() => {
      handler({
        type: ChatEventType.MEMBER_LEFT,
        payload: { roomId: "room-1", userId: "online-user" },
      })
    })

    await waitFor(() => {
      expect(screen.queryByTestId("avatar-online-user")).toBeNull()
    })
  })

  it("cleans up event subscription when roomId changes", async () => {
    const { rerender } = render(<RoomMembersSidebar roomId="room-1" />)

    await waitFor(() => {
      expect(screen.getByTestId("avatar-owner")).toBeTruthy()
    })

    const firstUnsub = chatHandlers[chatHandlers.length - 1].unsub

    rerender(<RoomMembersSidebar roomId="room-2" />)

    await waitFor(() => {
      expect(firstUnsub).toHaveBeenCalledTimes(1)
    })
  })
})