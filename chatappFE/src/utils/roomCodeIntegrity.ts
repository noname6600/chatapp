export type RoomCodeMap = Record<string, string>

export interface RoomCodeResponseContext {
  requestToken: number
  latestToken: number
  requestRoomId: string
  activeRoomId: string
}

/**
 * Keep user-provided join code unchanged in FE flow.
 */
export function preserveJoinCodeInput(input: string): string {
  return input
}

/**
 * Guard against out-of-order responses writing into wrong room context.
 */
export function shouldApplyRoomCodeResponse(
  context: RoomCodeResponseContext
): boolean {
  return (
    context.requestToken === context.latestToken &&
    context.requestRoomId === context.activeRoomId
  )
}

/**
 * Store room code by room id to avoid cross-room leakage.
 */
export function upsertRoomCodeByRoom(
  map: RoomCodeMap,
  roomId: string,
  code: string
): RoomCodeMap {
  return {
    ...map,
    [roomId]: code,
  }
}
