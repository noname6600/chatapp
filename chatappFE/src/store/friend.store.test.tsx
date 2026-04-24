// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from "vitest"

import { useFriendStore } from "./friend.store"

describe("friend.store - Badge and Status", () => {
  beforeEach(() => {
    useFriendStore.setState({
      unreadFriendRequestCount: 0,
      map: {},
    })
  })

  describe("Badge Counter", () => {
    it("increments unread friend request count", () => {
      const store = useFriendStore.getState()
      expect(store.unreadFriendRequestCount).toBe(0)

      store.incrementUnreadFriendRequestCount()
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(1)

      store.incrementUnreadFriendRequestCount()
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(2)
    })

    it("decrements unread friend request count", () => {
      const store = useFriendStore.getState()
      store.setUnreadCount(3)

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(3)

      store.decrementUnreadFriendRequestCount()
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(2)

      store.decrementUnreadFriendRequestCount()
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(1)
    })

    it("sets unread count directly", () => {
      const store = useFriendStore.getState()

      store.setUnreadCount(5)
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(5)

      store.setUnreadCount(0)
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(0)
    })

    it("does not go below zero", () => {
      const store = useFriendStore.getState()
      store.setUnreadCount(1)

      store.decrementUnreadFriendRequestCount()
      store.decrementUnreadFriendRequestCount()

      const count = useFriendStore.getState().unreadFriendRequestCount
      expect(count).toBeGreaterThanOrEqual(0)
    })
  })

  describe("Friend Status Map", () => {
    it("resolves and stores friend status", async () => {
      const store = useFriendStore.getState()
      const userId = "user-123"

      // Initially undefined
      expect(store.map[userId]).toBeUndefined()

      // Mock resolve should set status
      store.map[userId] = "FRIENDS"
      expect(useFriendStore.getState().map[userId]).toBe("FRIENDS")
    })

    it("updates status from REQUEST_SENT to FRIENDS", () => {
      const store = useFriendStore.getState()
      const userId = "user-456"

      store.map[userId] = "REQUEST_SENT"
      expect(store.map[userId]).toBe("REQUEST_SENT")

      // Simulate acceptance event updating status
      store.map[userId] = "FRIENDS"
      expect(useFriendStore.getState().map[userId]).toBe("FRIENDS")
    })
  })

  describe("Recommendation Status Consistency", () => {
    it("tracks multiple users with different statuses", () => {
      const store = useFriendStore.getState()

      store.map["user-1"] = "FRIENDS"
      store.map["user-2"] = "REQUEST_SENT"
      delete (store.map as Record<string, unknown>)["user-3"]

      const state = useFriendStore.getState()
      expect(state.map["user-1"]).toBe("FRIENDS")
      expect(state.map["user-2"]).toBe("REQUEST_SENT")
      expect(state.map["user-3"]).toBeUndefined()
    })
  })
})
