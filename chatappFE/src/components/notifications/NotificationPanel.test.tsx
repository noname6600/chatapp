/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"

import NotificationPanel from "./NotificationPanel"
import type { Notification } from "../../types/notification"

const mocks = vi.hoisted(() => ({
  roomsById: {} as Record<string, { id: string; name: string; type: string }>,
  users: {} as Record<string, { displayName?: string | null }>,
  fetchUsers: vi.fn(async () => {}),
}))

vi.mock("../../store/room.store", () => ({
  useRooms: () => ({ roomsById: mocks.roomsById }),
}))

vi.mock("../../store/user.store", () => ({
  useUserStore: (selector: (state: { users: typeof mocks.users; fetchUsers: typeof mocks.fetchUsers }) => unknown) =>
    selector({ users: mocks.users, fetchUsers: mocks.fetchUsers }),
}))

const baseNotification: Notification = {
  id: "n-1",
  type: "MENTION",
  referenceId: "m-1",
  roomId: "room-1",
  actorId: null,
  actorDisplayName: null,
  senderName: null,
  preview: null,
  isRead: false,
  createdAt: "2026-04-17T10:00:00.000Z",
}

const noop = async () => {}
const byExactTextContent = (expected: string) => (_: string, element: Element | null) =>
  element?.textContent === expected

afterEach(() => {
  cleanup()
  mocks.roomsById = {}
  mocks.users = {}
})

describe("NotificationPanel actor-name rendering", () => {
  it("shows actorDisplayName for MENTION when present", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, actorDisplayName: "Alice" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText(byExactTextContent("Alice mentioned you"))).toBeTruthy()
  })

  it("highlights pending friend-request unread count when provided", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, actorDisplayName: "Alice" }]}
        unreadCount={1}
        unreadFriendRequests={2}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText("Pending friend requests: 2")).toBeTruthy()
  })

  it("falls back to profile lookup when actorDisplayName is absent and actorId is set", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }
    mocks.users = { "actor-uuid": { displayName: "Bob" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, actorId: "actor-uuid" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText(byExactTextContent("Bob mentioned you"))).toBeTruthy()
  })

  it("falls back to senderName when it is not a UUID", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, senderName: "Carol" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText(byExactTextContent("Carol mentioned you"))).toBeTruthy()
  })

  it("does not show raw UUID as actor name", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    const uuidLike = "550e8400-e29b-41d4-a716-446655440000"
    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, senderName: uuidLike }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.queryByText(`${uuidLike} mentioned you`)).toBeNull()
    expect(screen.getByText(byExactTextContent("Someone mentioned you"))).toBeTruthy()
  })

  it("shows Someone when no actor info is available", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[baseNotification]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText(byExactTextContent("Someone mentioned you"))).toBeTruthy()
  })

  it("shows actor-aware copy for REACTION", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, type: "REACTION", actorDisplayName: "Dave" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText(byExactTextContent("Dave reacted to your message"))).toBeTruthy()
  })

  it("shows actor-aware copy for private MESSAGE (direct message)", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "Alice", type: "PRIVATE" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, type: "MESSAGE", actorDisplayName: "Alice" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText(byExactTextContent("Alice sent you a message"))).toBeTruthy()
  })

  it("keeps generic preview for non-private room MESSAGE", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, type: "MESSAGE", actorDisplayName: "Bob", preview: "Hello everyone" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText("Hello everyone")).toBeTruthy()
    expect(screen.queryByText("Bob sent you a message")).toBeNull()
  })

  it("shows room name as title for non-private MESSAGE", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "Engineering", type: "GROUP" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, type: "MESSAGE", actorDisplayName: "Bob", preview: "Hi" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    expect(screen.getByText("Engineering")).toBeTruthy()
  })

  it("shows actor name as title for private MESSAGE", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "Alice", type: "PRIVATE" } }

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, type: "MESSAGE", actorDisplayName: "Alice" }]}
        unreadCount={1}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    // Title and preview both show Alice as actor
    const aliceElements = screen.getAllByText("Alice")
    expect(aliceElements.length).toBeGreaterThanOrEqual(1)
  })

  it("requests older notifications when scrolled near the bottom", async () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }
    const onLoadMore = vi.fn(async () => {})

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, actorDisplayName: "Alice" }]}
        unreadCount={1}
        hasMore={true}
        isLoadingMore={false}
        onLoadMore={onLoadMore}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    const viewport = screen.getByTestId("notification-panel-scroll-viewport") as HTMLDivElement

    Object.defineProperty(viewport, "scrollHeight", { value: 500, configurable: true })
    Object.defineProperty(viewport, "clientHeight", { value: 200, configurable: true })
    Object.defineProperty(viewport, "scrollTop", { value: 220, configurable: true, writable: true })

    fireEvent.scroll(viewport)

    expect(onLoadMore).toHaveBeenCalledTimes(1)
  })

  it("does not request load-more while an older-page request is already in flight", () => {
    mocks.roomsById = { "room-1": { id: "room-1", name: "General", type: "GROUP" } }
    const onLoadMore = vi.fn(async () => {})

    render(
      <NotificationPanel
        notifications={[{ ...baseNotification, actorDisplayName: "Alice" }]}
        unreadCount={1}
        hasMore={true}
        isLoadingMore={true}
        onLoadMore={onLoadMore}
        onMarkAllRead={noop}
        onNotificationClick={noop}
      />
    )

    const viewport = screen.getByTestId("notification-panel-scroll-viewport") as HTMLDivElement
    Object.defineProperty(viewport, "scrollHeight", { value: 500, configurable: true })
    Object.defineProperty(viewport, "clientHeight", { value: 200, configurable: true })
    Object.defineProperty(viewport, "scrollTop", { value: 220, configurable: true, writable: true })

    fireEvent.scroll(viewport)

    expect(onLoadMore).not.toHaveBeenCalled()
  })
})
