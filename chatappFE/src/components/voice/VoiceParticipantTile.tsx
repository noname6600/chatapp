import { Mic, MicOff } from "lucide-react"
import type { VoiceParticipant } from "../../api/voice.service"

interface Props {
  participant: VoiceParticipant
  isSpeaking: boolean
  isMuted?: boolean
  isMe?: boolean
}

export default function VoiceParticipantTile({ participant, isSpeaking, isMuted, isMe }: Props) {
  return (
    <div className="flex flex-col items-center gap-1 w-16">
      <div
        className={`relative rounded-full transition-all duration-150 ${
          isSpeaking ? "ring-2 ring-green-400 ring-offset-1" : ""
        }`}
      >
        <img
          src={participant.avatarUrl || "/default-avatar.png"}
          alt={participant.username}
          className="w-12 h-12 rounded-full object-cover"
        />
        {isMuted && (
          <span className="absolute -bottom-0.5 -right-0.5 bg-red-500 rounded-full p-0.5">
            <MicOff size={10} className="text-white" />
          </span>
        )}
        {!isMuted && isSpeaking && (
          <span className="absolute -bottom-0.5 -right-0.5 bg-green-500 rounded-full p-0.5">
            <Mic size={10} className="text-white" />
          </span>
        )}
      </div>
      <span className="text-xs text-gray-700 truncate w-full text-center max-w-[4rem]">
        {isMe ? `${participant.username} (you)` : participant.username}
      </span>
    </div>
  )
}
