import { describe, expect, it } from "vitest"
import {
  preserveJoinCodeInput,
  shouldApplyRoomCodeResponse,
  upsertRoomCodeByRoom,
} from "./roomCodeIntegrity"

describe("roomCodeIntegrity", () => {
  it("preserves mixed-case join input unchanged", () => {
    expect(preserveJoinCodeInput("AbC123")).toBe("AbC123")
  })

  it("does not auto-uppercase lowercase input", () => {
    expect(preserveJoinCodeInput("abc123")).toBe("abc123")
  })

  it("stores room code by room id and prevents leakage", () => {
    const stateA = upsertRoomCodeByRoom({}, "room-a", "AAA111")
    const stateB = upsertRoomCodeByRoom(stateA, "room-b", "BBB222")

    expect(stateB["room-a"]).toBe("AAA111")
    expect(stateB["room-b"]).toBe("BBB222")
    expect(stateB["room-a"]).not.toBe(stateB["room-b"])
  })

  it("ignores stale room-code response by token mismatch", () => {
    const shouldApply = shouldApplyRoomCodeResponse({
      requestToken: 1,
      latestToken: 2,
      requestRoomId: "room-a",
      activeRoomId: "room-a",
    })

    expect(shouldApply).toBe(false)
  })
})
