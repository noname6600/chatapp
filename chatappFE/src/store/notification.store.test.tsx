/* @vitest-environment jsdom */

import { act, render, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useEffect } from "react"

import { NotificationProvider, useNotifications } from "./notification.store"
import { NotificationEventType } from "../constants/notificationEvents"
import type { Notification } from "../types/notification"

const mocks = vi.hoisted(() => ({
  getNotificationsApi: vi.fn(),
  markNotificationReadApi: vi.fn(),
  markAllNotificationsReadApi: vi.fn(),
  getRoomMuteSettingsApi: vi.fn(),
  muteRoomApi: vi.fn(),
  unmuteRoomApi: vi.fn(),
  connectNotificationSocket: vi.fn(),
  disconnectNotificationSocket: vi.fn(),
}))

let notificationEventHandler: ((event: { type: string; data: unknown }) => void) | null = null
let notificationOpenHandler: (() => void) | null = null

vi.mock("../api/notification.service", () => ({
  getNotificationsApi: mocks.getNotificationsApi,
  markNotificationReadApi: mocks.markNotificationReadApi,
  markAllNotificationsReadApi: mocks.markAllNotificationsReadApi,
  getRoomMuteSettingsApi: mocks.getRoomMuteSettingsApi,
  muteRoomApi: mocks.muteRoomApi,
  unmuteRoomApi: mocks.unmuteRoomApi,
}))

vi.mock("./auth.store", () => ({
  useAuth: () => ({
    accessToken: "token",
    userId: "me",
  }),
}))

vi.mock("../websocket/notification.socket", () => ({
  connectNotificationSocket: mocks.connectNotificationSocket,
  disconnectNotificationSocket: mocks.disconnectNotificationSocket,
  onNotificationEvent: (callback: (event: { type: string; data: unknown }) => void) => {
    notificationEventHandler = callback
    return () => {
      notificationEventHandler = null
    }
  },
  onNotificationSocketOpen: (callback: () => void) => {
    notificationOpenHandler = callback
    return () => {
      notificationOpenHandler = null
    }
  },
}))

function makeNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: "noti-1",
    type: "MESSAGE",
    referenceId: "ref-1",
    roomId: "room-1",
    senderName: "Alice",
    preview: "Hello",
    isRead: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function Probe({ onReady }: { onReady: (value: ReturnType<typeof useNotifications>) => void }) {
  const store = useNotifications()

  useEffect(() => {
    onReady(store)
  }, [store, onReady])

  return null
}

async function renderNotificationProvider() {
  let storeApi: ReturnType<typeof useNotifications> | null = null

  render(
    <NotificationProvider>
      <Probe onReady={(value) => {
        storeApi = value
      }} />
    </NotificationProvider>
  )

  await waitFor(() => expect(storeApi).not.toBeNull())

  return {
    get store() {
      if (!storeApi) {
        throw new Error("Notification store not initialized")
      }

      return storeApi
    },
  }
}

describe("notification.store", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    notificationEventHandler = null
    notificationOpenHandler = null
    mocks.getNotificationsApi.mockResolvedValue({ notifications: [], unreadCount: 0 })
    mocks.getRoomMuteSettingsApi.mockResolvedValue({ isMuted: false })
  })

  it("notification store increments unreadCount on NOTIFICATION_NEW", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-new-1", type: "MESSAGE", roomId: "room-1" }),
      })
    })

    expect(harness.store.unreadCount).toBe(1)
    expect(harness.store.notifications[0]?.id).toBe("noti-new-1")
  })

  it("self-sent room notification does not increment unread count", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-self-1",
          type: "MESSAGE",
          roomId: "room-1",
          senderId: "me",
        }),
      })
    })

    expect(harness.store.notifications[0]?.id).toBe("noti-self-1")
    expect(harness.store.unreadCount).toBe(0)
  })

  it("other-user room notification increments unread count", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-other-1",
          type: "MESSAGE",
          roomId: "room-1",
          senderId: "user2",
        }),
      })
    })

    expect(harness.store.notifications[0]?.id).toBe("noti-other-1")
    expect(harness.store.unreadCount).toBe(1)
  })

  it("muted room does not increment bell badge", async () => {
    mocks.getRoomMuteSettingsApi.mockResolvedValueOnce({ isMuted: true })

    const harness = await renderNotificationProvider()

    await act(async () => {
      await harness.store.fetchRoomMute("room-1")
    })

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-new-2", type: "MESSAGE", roomId: "room-1" }),
      })
    })

    expect(harness.store.notifications[0]?.id).toBe("noti-new-2")
    expect(harness.store.unreadCount).toBe(0)
  })

  it("reconnect reconciliation keeps newer realtime notifications", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-live-1",
          type: "MESSAGE",
          roomId: "room-1",
          createdAt: "2026-03-26T11:10:00.000Z",
        }),
      })
    })

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({
          id: "noti-stale-1",
          type: "MESSAGE",
          roomId: "room-1",
          createdAt: "2026-03-26T11:00:00.000Z",
        }),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      notificationOpenHandler?.()
    })

    await waitFor(() => {
      expect(harness.store.notifications.some((item) => item.id === "noti-live-1")).toBe(true)
    })

    expect(harness.store.notifications[0]?.id).toBe("noti-live-1")
    expect(harness.store.unreadCount).toBe(1)
  })

  it("ignores malformed realtime mention notifications without target context", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-mention-bad",
          type: "MENTION",
          roomId: null,
          referenceId: null,
        }),
      })
    })

    expect(harness.store.notifications.some((item) => item.id === "noti-mention-bad")).toBe(false)
    expect(harness.store.unreadCount).toBe(0)
  })
})