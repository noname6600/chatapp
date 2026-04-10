// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest"

import { 
  handleFriendshipEvent, 
  processFriendshipEvent,
  FriendshipEventType,
  type FriendshipWsEvent 
} from "./friendship.socket"
import { useFriendStore } from "../store/friend.store"

describe("friendship.socket - Event Handling", () => {
  beforeEach(() => {
    // Reset store state
    useFriendStore.setState({
      unreadFriendRequestCount: 0,
      map: {},
    })

    // Mock console methods to avoid spam
    vi.spyOn(console, "log").mockImplementation(() => {})
    vi.spyOn(console, "warn").mockImplementation(() => {})
  })

  describe("processFriendshipEvent", () => {
    it("increments unread count on FRIEND_REQUEST_RECEIVED", () => {
      const initialCount = useFriendStore.getState().unreadFriendRequestCount
      
      const event: FriendshipWsEvent = {
        type: FriendshipEventType.FRIEND_REQUEST_RECEIVED,
        data: { senderId: "user-1" },
      }

      processFriendshipEvent(event)

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(
        initialCount + 1
      )
    })

    it("decrements unread count on FRIEND_REQUEST_ACCEPTED", () => {
      useFriendStore.setState({ unreadFriendRequestCount: 2 })

      const event: FriendshipWsEvent = {
        type: FriendshipEventType.FRIEND_REQUEST_ACCEPTED,
        data: { senderId: "user-1" },
      }

      processFriendshipEvent(event)

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(1)
    })

    it("decrements unread count on FRIEND_REQUEST_DECLINED", () => {
      useFriendStore.setState({ unreadFriendRequestCount: 1 })

      const event: FriendshipWsEvent = {
        type: FriendshipEventType.FRIEND_REQUEST_DECLINED,
        data: {},
      }

      processFriendshipEvent(event)

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(0)
    })

    it("decrements unread count on FRIEND_REQUEST_CANCELLED", () => {
      useFriendStore.setState({ unreadFriendRequestCount: 2 })

      const event: FriendshipWsEvent = {
        type: FriendshipEventType.FRIEND_REQUEST_CANCELLED,
        data: {},
      }

      processFriendshipEvent(event)

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(1)
    })

    it("ignores FRIEND_STATUS_CHANGED events for add-friend context", () => {
      const initialCount = useFriendStore.getState().unreadFriendRequestCount
      
      const event: FriendshipWsEvent = {
        type: FriendshipEventType.FRIEND_STATUS_CHANGED,
        data: { newStatus: "BLOCKED", userLow: "u1", userHigh: "u2" },
      }

      processFriendshipEvent(event)

      // Count should remain unchanged
      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(
        initialCount
      )
    })
  })

  describe("handleFriendshipEvent", () => {
    it("processes valid friendship events", () => {
      const incrementSpy = vi.spyOn(
        useFriendStore.getState(),
        "incrementUnreadFriendRequestCount"
      )

      const msg = {
        type: FriendshipEventType.FRIEND_REQUEST_RECEIVED,
        data: { senderId: "user-1" },
      }

      handleFriendshipEvent(msg)

      expect(incrementSpy).toHaveBeenCalled()
      incrementSpy.mockRestore()
    })

    it("handles invalid events gracefully", () => {
      const msg = null

      // Should not throw
      expect(() => handleFriendshipEvent(msg)).not.toThrow()
    })

    it("handles missing type field", () => {
      const msg = { data: { senderId: "user-1" } }

      // Should not throw
      expect(() => handleFriendshipEvent(msg)).not.toThrow()
    })
  })

  describe("Badge Reconciliation", () => {
    it("can reconcile multiple incoming requests", () => {
      useFriendStore.setState({ unreadFriendRequestCount: 0 })

      const events: FriendshipWsEvent[] = [
        {
          type: FriendshipEventType.FRIEND_REQUEST_RECEIVED,
          data: { senderId: "user-1" },
        },
        {
          type: FriendshipEventType.FRIEND_REQUEST_RECEIVED,
          data: { senderId: "user-2" },
        },
        {
          type: FriendshipEventType.FRIEND_REQUEST_RECEIVED,
          data: { senderId: "user-3" },
        },
      ]

      events.forEach((event) => processFriendshipEvent(event))

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(3)
    })

    it("reconciles after mixed accept/decline events", () => {
      useFriendStore.setState({ unreadFriendRequestCount: 3 })

      processFriendshipEvent({
        type: FriendshipEventType.FRIEND_REQUEST_ACCEPTED,
        data: {},
      })
      processFriendshipEvent({
        type: FriendshipEventType.FRIEND_REQUEST_ACCEPTED,
        data: {},
      })
      processFriendshipEvent({
        type: FriendshipEventType.FRIEND_REQUEST_DECLINED,
        data: {},
      })

      expect(useFriendStore.getState().unreadFriendRequestCount).toBe(0)
    })
  })
})
