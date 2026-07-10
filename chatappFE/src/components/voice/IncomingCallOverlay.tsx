import { Phone, PhoneOff } from "lucide-react"
import { useCallSession } from "../../hooks/useCallSession"

export default function IncomingCallOverlay() {
  const { incoming, acceptCall, declineCall } = useCallSession()

  if (!incoming) return null

  const displayName = incoming.callerName ?? "Unknown"
  const avatar = incoming.callerAvatarUrl

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-2xl p-6 flex flex-col items-center gap-4 w-72">
        {avatar ? (
          <img src={avatar} alt={displayName} className="w-20 h-20 rounded-full object-cover ring-4 ring-green-400" />
        ) : (
          <div className="w-20 h-20 rounded-full bg-gray-300 dark:bg-gray-600 flex items-center justify-center text-2xl font-bold text-gray-600 dark:text-gray-200">
            {displayName[0]?.toUpperCase() ?? "?"}
          </div>
        )}
        <div className="text-center">
          <p className="text-lg font-semibold text-gray-900 dark:text-white">{displayName}</p>
          <p className="text-sm text-gray-500 dark:text-gray-400 animate-pulse">Incoming call…</p>
        </div>
        <div className="flex gap-6">
          <button
            onClick={() => declineCall(incoming.callId)}
            className="w-14 h-14 bg-red-500 hover:bg-red-600 rounded-full flex items-center justify-center text-white transition-colors"
            title="Decline"
          >
            <PhoneOff size={22} />
          </button>
          <button
            onClick={() => acceptCall(incoming.callId)}
            className="w-14 h-14 bg-green-500 hover:bg-green-600 rounded-full flex items-center justify-center text-white transition-colors"
            title="Accept"
          >
            <Phone size={22} />
          </button>
        </div>
      </div>
    </div>
  )
}
