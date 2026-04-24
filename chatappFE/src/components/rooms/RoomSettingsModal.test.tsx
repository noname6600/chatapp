/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import RoomSettingsModal from "./RoomSettingsModal"
import type { Room } from "../../types/room"

const mocks = vi.hoisted(() => ({
  loadRooms: vi.fn(async () => {}),
  fetchRoomNotificationMode: vi.fn(async () => "NO_RESTRICT"),
  setRoomNotificationMode: vi.fn(async () => {}),
  updateRoomApi: vi.fn(async () => {}),
  uploadRoomAvatarApi: vi.fn(async () => {}),
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({
    loadRooms: mocks.loadRooms,
  }),
}))

vi.mock("../../store/notification.store", () => ({
  useNotifications: () => ({
    notificationModesByRoom: { "room-1": "ONLY_MENTION" },
    fetchRoomNotificationMode: mocks.fetchRoomNotificationMode,
    setRoomNotificationMode: mocks.setRoomNotificationMode,
  }),
}))

vi.mock("../../api/room.service", () => ({
  updateRoomApi: mocks.updateRoomApi,
  uploadRoomAvatarApi: mocks.uploadRoomAvatarApi,
}))

const room: Room = {
  id: "room-1",
  type: "GROUP",
  name: "Design Squad",
  createdBy: "me",
  createdAt: "2026-04-17T00:00:00.000Z",
  myRole: "OWNER",
  unreadCount: 0,
  latestMessageAt: null,
  avatarUrl: null,
  otherUserId: null,
}

describe("RoomSettingsModal mute menu", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  it("opens compact mute menu and shows user-facing descriptions", async () => {
    render(<RoomSettingsModal isOpen room={room} onClose={() => {}} />)

    await waitFor(() => {
      expect(mocks.fetchRoomNotificationMode).toHaveBeenCalledWith("room-1")
    })

    fireEvent.click(screen.getAllByRole("button", { name: "Mute notifications" })[0])

    expect(screen.getByText("No restrict")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Only mentions notification mode" })).toBeTruthy()
    expect(screen.getByText("Nothing")).toBeTruthy()
    expect(
      screen.getByText("Only messages that mention you will appear as unread or notify.")
    ).toBeTruthy()
  })

  it("selects mode from menu and applies immediately", async () => {
    render(<RoomSettingsModal isOpen room={room} onClose={() => {}} />)

    fireEvent.click(screen.getAllByRole("button", { name: "Mute notifications" })[0])
    fireEvent.click(screen.getByRole("button", { name: "Nothing notification mode" }))

    await waitFor(() => {
      expect(mocks.setRoomNotificationMode).toHaveBeenCalledWith("room-1", "NOTHING")
    })
  })
})
