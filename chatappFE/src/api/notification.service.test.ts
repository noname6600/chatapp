import { beforeEach, describe, expect, it, vi } from "vitest"

const notificationApi = {
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}

const roomNotificationApi = {
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
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
        data: { isMuted: true },
      },
    })

    const { getRoomMuteSettingsApi } = await import("./notification.service")
    const result = await getRoomMuteSettingsApi("room-123")

    expect(roomNotificationApi.get).toHaveBeenCalledWith("/rooms/room-123/settings")
    expect(notificationApi.get).not.toHaveBeenCalledWith("/rooms/room-123/settings")
    expect(result).toEqual({ isMuted: true })
  })

  it("uses room-scoped gateway path for mute mutation", async () => {
    roomNotificationApi.post.mockResolvedValueOnce({
      data: {
        success: true,
      },
    })

    const { muteRoomApi } = await import("./notification.service")
    await muteRoomApi("room-123")

    expect(roomNotificationApi.post).toHaveBeenCalledWith("/rooms/room-123/mute")
    expect(notificationApi.post).not.toHaveBeenCalledWith("/rooms/room-123/mute")
  })

  it("uses room-scoped gateway path for unmute mutation", async () => {
    roomNotificationApi.delete.mockResolvedValueOnce({
      data: {
        success: true,
      },
    })

    const { unmuteRoomApi } = await import("./notification.service")
    await unmuteRoomApi("room-123")

    expect(roomNotificationApi.delete).toHaveBeenCalledWith("/rooms/room-123/mute")
    expect(notificationApi.delete).not.toHaveBeenCalledWith("/rooms/room-123/mute")
  })
})