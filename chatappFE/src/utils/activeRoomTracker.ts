let activeRoomId: string | null = null

export function setTrackedActiveRoom(roomId: string | null) {
  activeRoomId = roomId
}

export function getTrackedActiveRoom(): string | null {
  return activeRoomId
}
