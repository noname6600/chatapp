import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { getPinnedMessages } from "../api/chat.service"
import { ChatEventType } from "../constants/chatEvents"
import { onChatEvent } from "../websocket/chat.socket"
import type { PinnedMessage } from "../types/message"

export function usePinnedMessages(roomId: string | null) {
  const [pinnedMessages, setPinnedMessages] = useState<PinnedMessage[]>([])
  const inFlightRef = useRef(false)

  const loadPinnedMessages = useCallback(async () => {
    if (!roomId || inFlightRef.current) return
    inFlightRef.current = true

    try {
      const data = await getPinnedMessages(roomId)
      setPinnedMessages(data)
    } finally {
      inFlightRef.current = false
    }
  }, [roomId])

  useEffect(() => {
    if (!roomId) {
      setPinnedMessages([])
      return
    }

    void loadPinnedMessages()
  }, [roomId, loadPinnedMessages])

  useEffect(() => {
    if (!roomId) return

    return onChatEvent((event) => {
      if (
        event.type !== ChatEventType.MESSAGE_PINNED &&
        event.type !== ChatEventType.MESSAGE_UNPINNED
      ) {
        return
      }

      if (event.payload.roomId !== roomId) return

      // Server response remains source-of-truth and naturally de-dupes duplicate ws events.
      void loadPinnedMessages()
    })
  }, [roomId, loadPinnedMessages])

  const pinnedCount = useMemo(() => pinnedMessages.length, [pinnedMessages])

  return {
    pinnedMessages,
    pinnedCount,
    refreshPinnedMessages: loadPinnedMessages,
  }
}
