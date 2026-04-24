import { beforeEach, describe, expect, it, vi } from "vitest"

const notificationApi = {
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
  put: vi.fn(),
}

const roomNotificationApi = {
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
  put: vi.fn(),
}

vi.mock("./notification.api", () => ({
  notificationApi,
  roomNotificationApi,
}))

describe("notification.service", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("uses room-scoped gateway path for mute settings lookup", async () => {
    roomNotificationApi.get.mockResolvedValueOnce({
      data: {
        success: true,
        data: { mode: "NOTHING", isMuted: true },
      },
    })

    const { getRoomMuteSettingsApi } = await import("./notification.service")
    const result = await getRoomMuteSettingsApi("room-123")

    expect(roomNotificationApi.get).toHaveBeenCalledWith("/notifications/rooms/room-123/settings")
    expect(notificationApi.get).not.toHaveBeenCalledWith("/notifications/rooms/room-123/settings")
    expect(result).toEqual({ mode: "NOTHING", isMuted: true })
  })

  it("normalizes legacy read flag to isRead=false", async () => {
    notificationApi.get.mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          unreadCount: 1,
          notifications: [
            {
              id: "n-read-legacy",
              type: "MESSAGE",
              referenceId: "m-1",
              roomId: "room-1",
              senderName: "Alice",
              preview: "Hello",
              read: false,
              createdAt: "2026-05-01T10:00:00.000Z",
            },
          ],
        },
      },
    })

    const { getNotificationsApi } = await import("./notification.service")
    const result = await getNotificationsApi()

    expect(result.notifications[0]?.isRead).toBe(false)
  })

  it("defaults isRead=true when read flags are absent", async () => {
    notificationApi.get.mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          unreadCount: 0,
          notifications: [
            {
              id: "n-read-missing",
              type: "GROUP_INVITE",
              referenceId: "room-2",
              roomId: "room-2",
              senderName: "Bob",
              preview: null,
              createdAt: "2026-05-01T11:00:00.000Z",
            },
          ],
        },
      },
    })

    const { getNotificationsApi } = await import("./notification.service")
    const result = await getNotificationsApi()

    expect(result.notifications[0]?.isRead).toBe(true)
  })

  it("uses room-scoped gateway path for mode mutation", async () => {
    roomNotificationApi.put.mockResolvedValueOnce({
      data: {
        success: true,
        data: { mode: "ONLY_MENTION", isMuted: false },
      },
    })

    const { updateRoomNotificationModeApi } = await import("./notification.service")
    const result = await updateRoomNotificationModeApi("room-123", "ONLY_MENTION")

    expect(roomNotificationApi.put).toHaveBeenCalledWith("/notifications/rooms/room-123/settings", { mode: "ONLY_MENTION" })
    expect(notificationApi.put).not.toHaveBeenCalledWith("/notifications/rooms/room-123/settings", { mode: "ONLY_MENTION" })
    expect(result).toEqual({ mode: "ONLY_MENTION", isMuted: false })
  })
})
