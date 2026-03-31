import { describe, expect, it, vi, beforeEach } from "vitest"
import { editMessageApi } from "./chat.service"

const { putMock } = vi.hoisted(() => ({
  putMock: vi.fn(),
}))

vi.mock("./chat.api", () => ({
  chatApi: {
    put: putMock,
  },
}))

describe("editMessageApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("preserves mention tokens in serialized text blocks", async () => {
    putMock.mockResolvedValue({
      data: {
        success: true,
        data: {
          messageId: "m1",
          roomId: "r1",
          senderId: "u1",
          seq: 1,
          type: "MIXED",
          content: "hi @alice#user-2",
          replyToMessageId: null,
          createdAt: "2026-03-30T00:00:00.000Z",
          editedAt: "2026-03-30T00:01:00.000Z",
          deleted: false,
          attachments: [],
          blocks: [],
          reactions: [],
        },
        error: null,
        timestamp: "2026-03-30T00:01:00.000Z",
        traceId: "trace",
      },
    })

    await editMessageApi("m1", "hi @alice#user-2", [
      { type: "TEXT", text: "hi @alice#user-2" },
      {
        type: "ASSET",
        attachment: {
          type: "IMAGE",
          url: "https://example.com/a.jpg",
          fileName: "a.jpg",
        },
      },
    ])

    const [, payload] = putMock.mock.calls[0]
    expect(typeof payload.blocksJson).toBe("string")
    expect(payload.blocksJson).toContain("@alice#user-2")
  })
})
