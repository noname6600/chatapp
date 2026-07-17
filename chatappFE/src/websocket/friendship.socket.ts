import { useFriendStore } from "../store/friend.store"
import { onRealtimeEvent, onRealtimeOpen } from "./realtime.socket"

export enum FriendshipEventType {
  FRIEND_REQUEST_RECEIVED = "friendship.request.received",
  FRIEND_REQUEST_ACCEPTED = "friendship.request.accepted",
  FRIEND_REQUEST_DECLINED = "friendship.request.declined",
  FRIEND_REQUEST_CANCELLED = "friendship.request.cancelled",
  FRIEND_STATUS_CHANGED = "friendship.status.changed",
}

export interface FriendshipWsEvent {
  type: FriendshipEventType;
  data: Record<string, any>;
}

const getCurrentUserId = () => localStorage.getItem("my_user_id")

const getCounterpartyId = (event: FriendshipWsEvent) => {
  const currentUserId = getCurrentUserId()
  if (!currentUserId) return null

  if (event.data.senderId && event.data.recipientId) {
    return event.data.senderId === currentUserId
      ? event.data.recipientId
      : event.data.senderId
  }

  if (event.data.userLow && event.data.userHigh) {
    return event.data.userLow === currentUserId
      ? event.data.userHigh
      : event.data.userHigh === currentUserId
        ? event.data.userLow
        : null
  }

  return null
}

const eventHandlers = new Set<(event: FriendshipWsEvent) => void>()
const openHandlers = new Set<() => void>()

// Route incoming friendship messages from the unified socket
onRealtimeEvent((msg) => {
  if (!msg.type.startsWith("friendship.")) return
  handleFriendshipEvent({ type: msg.type, payload: msg.payload })
})

onRealtimeOpen(() => {
  openHandlers.forEach((h) => h())
})

export const onFriendshipEvent = (handler: (event: FriendshipWsEvent) => void) => {
  eventHandlers.add(handler)
  return () => eventHandlers.delete(handler)
}

export const onFriendshipSocketOpen = (handler: () => void) => {
  openHandlers.add(handler)
  return () => openHandlers.delete(handler)
}

export function handleFriendshipEvent(msg: any) {
  if (!msg || !msg.type) return

  const normalizedType = normalizeFriendshipEventType(msg.type)
  if (!normalizedType) return

  const event: FriendshipWsEvent = {
    type: normalizedType,
    data: msg.payload ?? {},
  }

  eventHandlers.forEach((handler) => {
    try {
      handler(event)
    } catch (err) {
      console.error("[friendship-socket] Handler error:", err)
    }
  })
}

export function processFriendshipEvent(event: FriendshipWsEvent) {
  const state = useFriendStore.getState()
  const counterpartyId = getCounterpartyId(event)

  switch (event.type) {
    case FriendshipEventType.FRIEND_REQUEST_RECEIVED: {
      state.incrementUnreadFriendRequestCount()
      if (counterpartyId) {
        state.setStatus(counterpartyId, "REQUEST_RECEIVED")
      }
      break
    }

    case FriendshipEventType.FRIEND_REQUEST_ACCEPTED: {
      state.decrementUnreadFriendRequestCount()
      if (counterpartyId) {
        state.setStatus(counterpartyId, "FRIENDS")
      }
      break
    }

    case FriendshipEventType.FRIEND_REQUEST_DECLINED:
    case FriendshipEventType.FRIEND_REQUEST_CANCELLED: {
      state.decrementUnreadFriendRequestCount()
      if (counterpartyId) {
        state.setStatus(counterpartyId, "NONE")
      }
      break
    }

    case FriendshipEventType.FRIEND_STATUS_CHANGED: {
      if (counterpartyId) {
        const eventType = event.data.eventType as string
        if (eventType === "friend.unfriended") {
          state.setStatus(counterpartyId, "NONE")
        } else if (eventType === "friend.blocked") {
          const myId = getCurrentUserId()
          if (event.data.actionUserId === myId) {
            state.setStatus(counterpartyId, "BLOCKED_BY_ME")
          } else {
            state.setStatus(counterpartyId, "BLOCKED_ME")
          }
        } else if (eventType === "friend.unblocked") {
          state.setStatus(counterpartyId, "NONE")
        }
      }
      break
    }

    default:
      break
  }
}

function normalizeFriendshipEventType(rawType: string): FriendshipEventType | null {
  switch (rawType) {
    case FriendshipEventType.FRIEND_REQUEST_RECEIVED:
      return FriendshipEventType.FRIEND_REQUEST_RECEIVED
    case FriendshipEventType.FRIEND_REQUEST_ACCEPTED:
      return FriendshipEventType.FRIEND_REQUEST_ACCEPTED
    case FriendshipEventType.FRIEND_REQUEST_DECLINED:
      return FriendshipEventType.FRIEND_REQUEST_DECLINED
    case FriendshipEventType.FRIEND_REQUEST_CANCELLED:
      return FriendshipEventType.FRIEND_REQUEST_CANCELLED
    case FriendshipEventType.FRIEND_STATUS_CHANGED:
      return FriendshipEventType.FRIEND_STATUS_CHANGED
    default:
      return null
  }
}
