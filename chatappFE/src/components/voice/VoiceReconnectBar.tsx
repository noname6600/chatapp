import { useEffect, useState } from "react"
import { AlertTriangle, Loader2 } from "lucide-react"
import { useVoiceStore } from "../../store/voice.store"
import { useVoiceRoom } from "../../hooks/useVoiceRoom"
import { useCallSession } from "../../hooks/useCallSession"
import { useRooms } from "../../store/room.store"
import { getMyActiveVoiceRoomApi } from "../../api/voice.service"
import { Button } from "../ui/Button"

const POLL_INTERVAL_MS = 20_000

export default function VoiceReconnectBar() {
  const reconnectPrompt = useVoiceStore((s) => s.reconnectPrompt)
  const setReconnectPrompt = useVoiceStore((s) => s.setReconnectPrompt)
  const storeActiveVoiceRoomId = useVoiceStore((s) => s.activeVoiceRoomId)
  const storeIsConnected = useVoiceStore((s) => s.isConnected)
  const { roomsById } = useRooms()
  const {
    active: activeCall,
    outgoing: outgoingCall,
    incoming: incomingCall,
    endCall,
    cancelCall,
    declineCall,
  } = useCallSession()
  const [connecting, setConnecting] = useState(false)
  // "room" = this tab is already connected to a different voice room; "call" = on a call
  const [switchConfirmReason, setSwitchConfirmReason] = useState<"room" | "call" | null>(null)

  const { join } = useVoiceRoom(reconnectPrompt?.chatRoomId ?? null)

  // Detect "recorded active in a room this tab isn't the live connection for"
  // — either another device has it, or a refresh left a stale record behind.
  // Polls rather than relying on a realtime event, since it needs to work
  // for a device that wasn't even watching this account's rooms before now.
  useEffect(() => {
    let cancelled = false
    const poll = async () => {
      try {
        const result = await getMyActiveVoiceRoomApi()
        if (cancelled) return
        if (!result) {
          setReconnectPrompt(null)
          return
        }
        const alreadyLiveHere =
          useVoiceStore.getState().isConnected &&
          useVoiceStore.getState().activeVoiceRoomId === result.chatRoomId
        setReconnectPrompt(alreadyLiveHere ? null : result)
      } catch {
        // best-effort — leave existing prompt state alone on a transient failure
      }
    }
    poll()
    const interval = setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [setReconnectPrompt])

  // Actively clear the prompt the moment this tab becomes the live connection
  // for that room — not just hide it. Leaving it in place and only hiding it
  // via the render check below meant a later, perfectly normal Leave (which
  // drops isConnected back to false) made this stale prompt reappear, since
  // hiding it never actually cleared the underlying state.
  useEffect(() => {
    if (reconnectPrompt && storeIsConnected && storeActiveVoiceRoomId === reconnectPrompt.chatRoomId) {
      setReconnectPrompt(null)
    }
  }, [reconnectPrompt, storeIsConnected, storeActiveVoiceRoomId, setReconnectPrompt])

  if (!reconnectPrompt) return null
  // Defensive re-check so the bar disappears immediately on a successful
  // reconnect instead of waiting for the effect above/next poll tick.
  if (storeIsConnected && storeActiveVoiceRoomId === reconnectPrompt.chatRoomId) return null

  const roomName = roomsById[reconnectPrompt.chatRoomId]?.name ?? "a voice channel"

  const doReconnect = async () => {
    setConnecting(true)
    try {
      await join()
    } finally {
      setConnecting(false)
    }
  }

  const handleClick = () => {
    if (activeCall || outgoingCall || incomingCall) {
      setSwitchConfirmReason("call")
    } else if (storeActiveVoiceRoomId && storeActiveVoiceRoomId !== reconnectPrompt.chatRoomId) {
      setSwitchConfirmReason("room")
    } else {
      doReconnect()
    }
  }

  const confirmSwitch = async () => {
    setSwitchConfirmReason(null)
    if (activeCall) await endCall(activeCall.callId)
    else if (outgoingCall) await cancelCall(outgoingCall.callId)
    else if (incomingCall) await declineCall(incomingCall.callId)
    doReconnect()
  }

  return (
    <>
      <div className="flex-shrink-0 flex items-center justify-between px-4 py-2 bg-amber-500 text-white shadow-lg">
        <div className="flex items-center gap-2 min-w-0">
          <AlertTriangle size={16} className="flex-shrink-0" />
          <p className="text-sm font-semibold truncate">
            {reconnectPrompt.liveElsewhere
              ? `You're connected to ${roomName} on another device.`
              : `You're on ${roomName}.`}
          </p>
        </div>
        <button
          onClick={handleClick}
          disabled={connecting}
          className="flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-600 hover:bg-amber-700 disabled:opacity-60 text-white text-xs font-medium transition-colors"
        >
          {connecting && <Loader2 size={13} className="animate-spin" />}
          Reconnect here
        </button>
      </div>

      {switchConfirmReason && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full space-y-4">
            <div className="flex items-center gap-3">
              <AlertTriangle size={24} className="text-amber-500" />
              <h2 className="text-lg font-semibold text-gray-900">
                {switchConfirmReason === "call" ? "Leave call to reconnect?" : "Switch voice rooms?"}
              </h2>
            </div>

            <p className="text-sm text-gray-600">
              {switchConfirmReason === "call"
                ? "You have an active call. Reconnecting to this voice room will end it."
                : "You're already in another voice room. Reconnecting to this one will leave that room."}{" "}
              Are you sure you want to continue?
            </p>

            <div className="flex gap-3 justify-end">
              <Button variant="outline" size="sm" onClick={() => setSwitchConfirmReason(null)}>
                Cancel
              </Button>
              <Button variant="destructive" size="sm" onClick={confirmSwitch}>
                {switchConfirmReason === "call" ? "End Call & Reconnect" : "Leave & Reconnect"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
