/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, within } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import type { Room } from "../../types/room"
import RoomList from "./RoomList"

const chatState = {
  activeRoomId: null as string | null,
  setActiveRoom: vi.fn(),
}

const roomState = {
  roomsById: {} as Record<string, Room>,
  roomOrder: [] as string[],
}

const makeRoom = (overrides: Partial<Room>): Room => ({
  id: overrides.id ?? "room-1",
  type: overrides.type ?? "PRIVATE",
  name: overrides.name ?? "Room",
  avatarUrl: overrides.avatarUrl ?? null,
  createdBy: overrides.createdBy ?? "system",
  createdAt: overrides.createdAt ?? "2026-04-14T00:00:00.000Z",
  myRole: overrides.myRole ?? "MEMBER",
  unreadCount: overrides.unreadCount ?? 0,
  latestMessageAt: overrides.latestMessageAt ?? "2026-04-14T01:00:00.000Z",
  otherUserId: overrides.otherUserId ?? "user-2",
  lastMessage: overrides.lastMessage ?? {
    id: "msg-1",
    senderId: "user-2",
    senderName: "Alex",
    content: "Latest preview",
    createdAt: "2026-04-14T01:00:00.000Z",
  },
})

vi.mock("../../store/chat.store", () => ({
  useChat: () => chatState,
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => roomState,
}))

vi.mock("./ConversationModal", () => ({
  default: ({ isOpen }: { isOpen: boolean }) => (isOpen ? <div>Conversation Modal</div> : null),
}))

vi.mock("./GroupRoomItem", () => ({
  default: ({ room, onClick }: { room: Room; onClick: () => void }) => (
    <button type="button" onClick={onClick}>
      {room.name}
    </button>
  ),
}))

vi.mock("./PrivateRoomItem", () => ({
  default: ({ room, onClick }: { room: Room; onClick: () => void }) => (
    <button type="button" onClick={onClick}>
      {room.name}
    </button>
  ),
}))

describe("RoomList", () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    chatState.activeRoomId = null
    chatState.setActiveRoom.mockReset()
    roomState.roomsById = {}
    roomState.roomOrder = []
  })

  it("bounds the groups section when both groups and direct messages are expanded", () => {
    roomState.roomsById = {
      "group-1": makeRoom({ id: "group-1", type: "GROUP", name: "Design Team", otherUserId: null }),
      "group-2": makeRoom({ id: "group-2", type: "GROUP", name: "Backend Guild", otherUserId: null }),
      "dm-1": makeRoom({ id: "dm-1", type: "PRIVATE", name: "Alex", otherUserId: "alex" }),
      "dm-2": makeRoom({ id: "dm-2", type: "PRIVATE", name: "Jamie", otherUserId: "jamie" }),
    }
    roomState.roomOrder = ["group-1", "group-2", "dm-1", "dm-2"]

    render(<RoomList />)

    expect(screen.getByTestId("room-list-groups-section").className).toContain("max-h-[45%]")
    expect(screen.getByTestId("room-list-groups-body").className).toContain("overflow-y-auto")
    expect(screen.getByTestId("room-list-dms-section").className).toContain("flex-1")
    expect(screen.getByTestId("room-list-dms-body").className).toContain("overflow-y-auto")
  })

  it("lets direct messages use the available height when groups are collapsed", () => {
    roomState.roomsById = {
      "group-1": makeRoom({ id: "group-1", type: "GROUP", name: "Design Team", otherUserId: null }),
      "dm-1": makeRoom({ id: "dm-1", type: "PRIVATE", name: "Alex", otherUserId: "alex" }),
    }
    roomState.roomOrder = ["group-1", "dm-1"]

    render(<RoomList />)

    const groupsSection = screen.getByTestId("room-list-groups-section")
    fireEvent.click(within(groupsSection).getByRole("button", { name: /Groups/i }))

    expect(screen.queryByTestId("room-list-groups-body")).toBeNull()
    expect(groupsSection.className).not.toContain("max-h-[45%]")
    expect(screen.getByTestId("room-list-dms-section").className).toContain("flex-1")
  })

  it("lets the groups section expand when direct messages are empty", () => {
    roomState.roomsById = {
      "group-1": makeRoom({ id: "group-1", type: "GROUP", name: "Design Team", otherUserId: null }),
      "group-2": makeRoom({ id: "group-2", type: "GROUP", name: "Backend Guild", otherUserId: null }),
    }
    roomState.roomOrder = ["group-1", "group-2"]

    render(<RoomList />)

    expect(screen.getByTestId("room-list-groups-section").className).toContain("flex-1")
    expect(screen.getByTestId("room-list-groups-section").className).not.toContain("max-h-[45%]")
    expect(screen.getByText("No direct messages yet.")).toBeTruthy()
  })
})
