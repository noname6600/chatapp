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
  clearRoomNotificationsApi: vi.fn(),
  getRoomMuteSettingsApi: vi.fn(),
  updateRoomNotificationModeApi: vi.fn(),
  connectNotificationSocket: vi.fn(),
  disconnectNotificationSocket: vi.fn(),
}))

let notificationEventHandler: ((event: { type: string; data: unknown }) => void) | null = null
let notificationOpenHandler: (() => void) | null = null

vi.mock("../api/notification.service", () => ({
  getNotificationsApi: mocks.getNotificationsApi,
  markNotificationReadApi: mocks.markNotificationReadApi,
  markAllNotificationsReadApi: mocks.markAllNotificationsReadApi,
  clearRoomNotificationsApi: mocks.clearRoomNotificationsApi,
  getRoomMuteSettingsApi: mocks.getRoomMuteSettingsApi,
  updateRoomNotificationModeApi: mocks.updateRoomNotificationModeApi,
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
    localStorage.clear()
    notificationEventHandler = null
    notificationOpenHandler = null
    mocks.getNotificationsApi.mockResolvedValue({ notifications: [], unreadCount: 0 })
    mocks.getRoomMuteSettingsApi.mockResolvedValue({ mode: "NO_RESTRICT", isMuted: false })
    mocks.updateRoomNotificationModeApi.mockResolvedValue({ mode: "NO_RESTRICT", isMuted: false })
    mocks.clearRoomNotificationsApi.mockResolvedValue(undefined)
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

  it("supports realtime unread increments for REPLY, REACTION, GROUP_INVITE, and FRIEND_REQUEST_ACCEPTED", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-reply-1", type: "REPLY", roomId: "room-1" }),
      })
    })
    expect(harness.store.unreadCount).toBe(1)

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-reaction-1", type: "REACTION", roomId: "room-1" }),
      })
    })
    expect(harness.store.unreadCount).toBe(2)

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-invite-1", type: "GROUP_INVITE", roomId: "room-2" }),
      })
    })
    expect(harness.store.unreadCount).toBe(3)

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-fr-accepted-1", type: "FRIEND_REQUEST_ACCEPTED", roomId: null }),
      })
    })
    expect(harness.store.unreadCount).toBe(4)
  })

  it("keeps MESSAGE notifications lower priority than mention-only mode policy", async () => {
    mocks.getRoomMuteSettingsApi.mockResolvedValueOnce({ mode: "ONLY_MENTION", isMuted: false })
    const harness = await renderNotificationProvider()

    await act(async () => {
      await harness.store.fetchRoomNotificationMode("room-1")
    })

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-message-policy", type: "MESSAGE", roomId: "room-1" }),
      })
    })

    expect(harness.store.unreadCount).toBe(0)
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

  it("non-active room MESSAGE is inserted immediately and increments badge", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-room-outside-1",
          type: "MESSAGE",
          roomId: "room-outside",
          senderId: "user-2",
        }),
      })
    })

    expect(harness.store.notifications[0]?.id).toBe("noti-room-outside-1")
    expect(harness.store.unreadCount).toBe(1)
  })

  it("muted room does not increment bell badge", async () => {
    mocks.getRoomMuteSettingsApi.mockResolvedValueOnce({ mode: "NOTHING", isMuted: true })

    const harness = await renderNotificationProvider()

    await act(async () => {
      await harness.store.fetchRoomNotificationMode("room-1")
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

  it("mention-only mode ignores non-mention notifications and counts mentions", async () => {
    mocks.getRoomMuteSettingsApi.mockResolvedValueOnce({ mode: "ONLY_MENTION", isMuted: false })
    const harness = await renderNotificationProvider()

    await act(async () => {
      await harness.store.fetchRoomNotificationMode("room-1")
    })

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-msg-only", type: "MESSAGE", roomId: "room-1" }),
      })
    })
    expect(harness.store.unreadCount).toBe(0)

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({ id: "noti-mention-only", type: "MENTION", roomId: "room-1" }),
      })
    })
    expect(harness.store.unreadCount).toBe(1)
  })

  it("nothing mode suppresses mentions during reconnect reconciliation", async () => {
    const harness = await renderNotificationProvider()

    mocks.updateRoomNotificationModeApi.mockResolvedValueOnce({ mode: "NOTHING", isMuted: true })
    await act(async () => {
      await harness.store.setRoomNotificationMode("room-1", "NOTHING")
    })

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({
          id: "noti-reconcile-mention",
          type: "MENTION",
          roomId: "room-1",
          createdAt: "2026-04-17T01:00:00.000Z",
        }),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      notificationOpenHandler?.()
    })

    await waitFor(() => {
      expect(harness.store.notifications.some((n) => n.id === "noti-reconcile-mention")).toBe(true)
      expect(harness.store.unreadCount).toBe(0)
    })
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
    expect(harness.store.unreadCount).toBe(2)
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

  it("pins unread action-required friend requests above newer notifications", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-message-new",
          type: "MESSAGE",
          roomId: "room-1",
          createdAt: "2026-04-17T10:00:00.000Z",
        }),
      })
    })

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-friend-request-old",
          type: "FRIEND_REQUEST",
          roomId: null,
          referenceId: "user-2",
          actionRequired: true,
          createdAt: "2026-04-17T09:00:00.000Z",
        }),
      })
    })

    expect(harness.store.notifications[0]?.id).toBe("noti-friend-request-old")
    expect(harness.store.unreadCount).toBe(2)
  })

  it("markAllRead keeps only FRIEND_REQUEST unread and clears other types", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-message-1",
          type: "MESSAGE",
          roomId: "room-1",
          isRead: false,
          createdAt: "2026-04-17T09:00:00.000Z",
        }),
      })
    })

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-invite-1",
          type: "GROUP_INVITE",
          roomId: "room-1",
          actionRequired: true,
          isRead: false,
          createdAt: "2026-04-17T08:30:00.000Z",
        }),
      })
    })

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-friend-request-1",
          type: "FRIEND_REQUEST",
          roomId: null,
          referenceId: "user-2",
          actionRequired: true,
          isRead: false,
          createdAt: "2026-04-17T08:00:00.000Z",
        }),
      })
    })

    expect(harness.store.unreadCount).toBe(3)

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({
          id: "noti-friend-request-1",
          type: "FRIEND_REQUEST",
          roomId: null,
          referenceId: "user-2",
          actionRequired: true,
          isRead: false,
          createdAt: "2026-04-17T08:00:00.000Z",
        }),
        makeNotification({
          id: "noti-invite-1",
          type: "GROUP_INVITE",
          roomId: "room-1",
          actionRequired: true,
          isRead: true,
          createdAt: "2026-04-17T08:30:00.000Z",
        }),
        makeNotification({
          id: "noti-message-1",
          type: "MESSAGE",
          roomId: "room-1",
          isRead: true,
          createdAt: "2026-04-17T09:00:00.000Z",
        }),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      await harness.store.markAllRead()
    })

    expect(mocks.markAllNotificationsReadApi).toHaveBeenCalledTimes(1)
    expect(harness.store.unreadCount).toBe(1)

    const friendRequest = harness.store.notifications.find((n) => n.id === "noti-friend-request-1")
    const invite = harness.store.notifications.find((n) => n.id === "noti-invite-1")
    const message = harness.store.notifications.find((n) => n.id === "noti-message-1")

    expect(friendRequest?.isRead).toBe(false)
    expect(invite?.isRead).toBe(true)
    expect(message?.isRead).toBe(true)
  })

  it("mark-all-read: pending scope prevents stale server fetch from restoring unread state", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-convergence-1",
          type: "MESSAGE",
          roomId: "room-1",
          isRead: false,
          createdAt: "2026-05-01T10:00:00.000Z",
        }),
      })
    })

    expect(harness.store.unreadCount).toBe(1)

    // Server returns the notification still as unread (race condition — server hasn't caught up)
    mocks.getNotificationsApi.mockResolvedValue({
      notifications: [
        makeNotification({
          id: "noti-convergence-1",
          type: "MESSAGE",
          roomId: "room-1",
          isRead: false,
          createdAt: "2026-05-01T10:00:00.000Z",
        }),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      await harness.store.markAllRead()
    })

    await waitFor(() => {
      expect(mocks.markAllNotificationsReadApi).toHaveBeenCalledTimes(1)
      expect(harness.store.unreadCount).toBe(0)

      const friendRequest = harness.store.notifications.find((n) => n.id === "noti-convergence-1")
      expect(friendRequest?.isRead).toBe(true)
    })
  })

  it("room-clear: pending scope prevents stale sync from restoring room notifications as unread", async () => {
    const harness = await renderNotificationProvider()

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-room-1",
          type: "MESSAGE",
          roomId: "room-42",
          isRead: false,
          createdAt: "2026-05-01T10:00:00.000Z",
        }),
      })
    })
    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-room-2",
          type: "MENTION",
          roomId: "room-99",
          isRead: false,
          createdAt: "2026-05-01T09:00:00.000Z",
        }),
      })
    })

    expect(harness.store.unreadCount).toBe(2)

    // Server has both notifications still unread; set up BEFORE triggering clear
    mocks.getNotificationsApi.mockResolvedValue({
      notifications: [
        makeNotification({ id: "noti-room-1", type: "MESSAGE", roomId: "room-42", isRead: false, createdAt: "2026-05-01T10:00:00.000Z" }),
        makeNotification({ id: "noti-room-2", type: "MENTION", roomId: "room-99", isRead: false, createdAt: "2026-05-01T09:00:00.000Z" }),
      ],
      unreadCount: 2,
    })

    // Trigger clear; the scope should protect room-42 even when sync returns stale unread data
    await act(async () => {
      await harness.store.clearRoomNotifications("room-42")
    })

    // Both notifications should exist in state (from the stale sync)
    await waitFor(() => {
      expect(harness.store.notifications.some((n) => n.id === "noti-room-1")).toBe(true)
      expect(harness.store.notifications.some((n) => n.id === "noti-room-2")).toBe(true)
    })

    const cleared = harness.store.notifications.find((n) => n.id === "noti-room-1")
    const unrelated = harness.store.notifications.find((n) => n.id === "noti-room-2")

    // Room-42 must remain read despite stale server data; room-99 must remain unread
    expect(cleared?.isRead).toBe(true)
    expect(unrelated?.isRead).toBe(false)
  })

  it("mark-all-read keeps only existing unresolved friend requests when stale ones disappear", async () => {
    const harness = await renderNotificationProvider()

    for (let i = 1; i <= 10; i++) {
      await act(async () => {
        notificationEventHandler?.({
          type: NotificationEventType.NOTIFICATION_NEW,
          data: makeNotification({
            id: `noti-msg-${i}`,
            type: "MESSAGE",
            roomId: "room-1",
            isRead: false,
            createdAt: `2026-05-01T09:${String(i).padStart(2, "0")}:00.000Z`,
          }),
        })
      })
    }

    for (let i = 1; i <= 4; i++) {
      await act(async () => {
        notificationEventHandler?.({
          type: NotificationEventType.NOTIFICATION_NEW,
          data: makeNotification({
            id: `noti-fr-${i}`,
            type: "FRIEND_REQUEST",
            roomId: null,
            referenceId: `user-${i}`,
            actionRequired: true,
            isRead: false,
            createdAt: `2026-05-01T08:${String(i).padStart(2, "0")}:00.000Z`,
          }),
        })
      })
    }

    expect(harness.store.unreadCount).toBe(14)

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({
          id: "noti-fr-1",
          type: "FRIEND_REQUEST",
          roomId: null,
          referenceId: "user-1",
          actionRequired: true,
          isRead: false,
          createdAt: "2026-05-01T08:01:00.000Z",
        }),
        ...Array.from({ length: 10 }, (_, idx) =>
          makeNotification({
            id: `noti-msg-${idx + 1}`,
            type: "MESSAGE",
            roomId: "room-1",
            isRead: true,
            createdAt: `2026-05-01T09:${String(idx + 1).padStart(2, "0")}:00.000Z`,
          })
        ),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      await harness.store.markAllRead()
    })

    const friendRequestIds = harness.store.notifications
      .filter((n) => n.type === "FRIEND_REQUEST")
      .map((n) => n.id)

    expect(friendRequestIds).toEqual(["noti-fr-1"])
    expect(harness.store.unreadCount).toBe(1)
  })

  it("reconnect sync does not resurrect stale friend requests or unread count", async () => {
    const harness = await renderNotificationProvider()

    for (let i = 1; i <= 4; i++) {
      await act(async () => {
        notificationEventHandler?.({
          type: NotificationEventType.NOTIFICATION_NEW,
          data: makeNotification({
            id: `stale-fr-${i}`,
            type: "FRIEND_REQUEST",
            roomId: null,
            referenceId: `user-${i}`,
            actionRequired: true,
            isRead: false,
            createdAt: `2026-05-01T08:${String(i).padStart(2, "0")}:00.000Z`,
          }),
        })
      })
    }

    for (let i = 1; i <= 10; i++) {
      await act(async () => {
        notificationEventHandler?.({
          type: NotificationEventType.NOTIFICATION_NEW,
          data: makeNotification({
            id: `stale-msg-${i}`,
            type: "MESSAGE",
            roomId: "room-1",
            isRead: false,
            createdAt: `2026-05-01T09:${String(i).padStart(2, "0")}:00.000Z`,
          }),
        })
      })
    }

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "live-after-reconnect",
          type: "MESSAGE",
          roomId: "room-2",
          isRead: false,
          createdAt: "2026-05-01T10:30:00.000Z",
        }),
      })
    })

    expect(harness.store.unreadCount).toBe(15)

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({
          id: "stale-fr-1",
          type: "FRIEND_REQUEST",
          roomId: null,
          referenceId: "user-1",
          actionRequired: true,
          isRead: false,
          createdAt: "2026-05-01T08:01:00.000Z",
        }),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      notificationOpenHandler?.()
    })

    await waitFor(() => {
      const friendRequests = harness.store.notifications.filter((n) => n.type === "FRIEND_REQUEST")
      expect(friendRequests).toHaveLength(1)
      expect(friendRequests[0]?.id).toBe("stale-fr-1")
      expect(harness.store.unreadCount).toBe(2)
    })
  })

  it("mergeNotifications produces deterministic order when createdAt timestamps are equal", async () => {
    const harness = await renderNotificationProvider()

    // Two notifications with the same timestamp — should be ordered by id DESC
    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-aaa-001",
          type: "MESSAGE",
          roomId: "room-1",
          createdAt: "2026-05-01T10:00:00.000Z",
        }),
      })
    })
    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.NOTIFICATION_NEW,
        data: makeNotification({
          id: "noti-zzz-999",
          type: "MESSAGE",
          roomId: "room-2",
          createdAt: "2026-05-01T10:00:00.000Z",
        }),
      })
    })

    const ids = harness.store.notifications.map((n) => n.id)
    // id DESC: "noti-zzz-999" > "noti-aaa-001"
    expect(ids.indexOf("noti-zzz-999")).toBeLessThan(ids.indexOf("noti-aaa-001"))
  })

  it("refresh baseline from API prevents stale unread websocket increase regression", async () => {
    const harness = await renderNotificationProvider()

    for (let i = 1; i <= 13; i++) {
      await act(async () => {
        notificationEventHandler?.({
          type: NotificationEventType.NOTIFICATION_NEW,
          data: makeNotification({
            id: `old-unread-${i}`,
            type: "MESSAGE",
            roomId: "room-1",
            isRead: false,
            createdAt: `2026-05-01T09:${String(i).padStart(2, "0")}:00.000Z`,
          }),
        })
      })
    }

    expect(harness.store.unreadCount).toBe(13)

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({
          id: "only-one-unread",
          type: "FRIEND_REQUEST",
          roomId: null,
          referenceId: "user-1",
          actionRequired: true,
          isRead: false,
          createdAt: "2026-05-01T10:00:00.000Z",
        }),
      ],
      unreadCount: 1,
    })

    await act(async () => {
      await harness.store.fetchNotifications()
    })

    expect(harness.store.unreadCount).toBe(1)

    await act(async () => {
      notificationEventHandler?.({
        type: NotificationEventType.UNREAD_COUNT_UPDATE,
        data: { unreadCount: 13 },
      })
    })

    expect(harness.store.unreadCount).toBe(1)
  })

  it("loadMoreNotifications appends older page with id dedupe and stable ordering", async () => {
    const newestTimestamp = "2026-05-02T10:00:00.000Z"
    const olderTimestamp = "2026-05-02T09:00:00.000Z"

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({ id: "noti-new-1", createdAt: newestTimestamp, roomId: "room-1", type: "MESSAGE" }),
      ],
      unreadCount: 1,
      page: 0,
      size: 50,
      hasMore: true,
      nextPage: 1,
      windowCreatedAt: newestTimestamp,
    })

    const harness = await renderNotificationProvider()

    await act(async () => {
      await harness.store.fetchNotifications()
    })

    await waitFor(() => {
      expect(harness.store.notifications.map((item) => item.id)).toEqual(["noti-new-1"])
      expect(harness.store.hasMoreNotifications).toBe(true)
    })

    mocks.getNotificationsApi.mockResolvedValueOnce({
      notifications: [
        makeNotification({ id: "noti-new-1", createdAt: newestTimestamp, roomId: "room-1", type: "MESSAGE" }),
        makeNotification({ id: "noti-old-1", createdAt: olderTimestamp, roomId: "room-2", type: "MESSAGE" }),
      ],
      unreadCount: 1,
      page: 1,
      size: 50,
      hasMore: false,
      nextPage: null,
      windowCreatedAt: newestTimestamp,
    })

    await act(async () => {
      await harness.store.loadMoreNotifications()
    })

    const ids = harness.store.notifications.map((item) => item.id)
    expect(ids).toEqual(["noti-new-1", "noti-old-1"])
    expect(harness.store.hasMoreNotifications).toBe(false)
    expect(harness.store.isLoadingMoreNotifications).toBe(false)
  })
})
