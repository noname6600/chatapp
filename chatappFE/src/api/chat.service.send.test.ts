import { beforeEach, describe, expect, it, vi } from "vitest"

import { sendMessageApi } from "./chat.service"

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}))

vi.mock("./chat.api", () => ({
  chatApi: {
    post: postMock,
  },
}))

describe("sendMessageApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("normalizes ROOM_INVITE block payload and preserves invite metadata", async () => {
    postMock.mockResolvedValue({
      data: {
        success: true,
        data: {
          messageId: "m1",
          roomId: "r1",
          senderId: "u1",
          seq: 10,
          type: "MIXED",
          content: "[Group Invite: Dev Room]",
          replyToMessageId: null,
          createdAt: "2026-04-09T00:00:00.000Z",
          editedAt: null,
          deleted: false,
          attachments: [],
          blocks: [],
          reactions: [],
        },
        error: null,
        timestamp: "2026-04-09T00:00:00.000Z",
        traceId: "trace",
      },
    })

    await sendMessageApi({
      roomId: "r1",
      content: "",
      blocks: [
        {
          type: "ROOM_INVITE",
          roomInvite: {
            roomId: "room-target",
            roomName: "Dev Room",
            roomAvatarUrl: "https://example.com/avatar.png",
            memberCount: 42,
          },
        },
      ],
    })

    expect(postMock).toHaveBeenCalledTimes(1)

    const [, payload] = postMock.mock.calls[0]
    expect(payload.blocks).toEqual([
      {
        type: "ROOM_INVITE",
        roomInvite: {
          roomId: "room-target",
          roomName: "Dev Room",
          roomAvatarUrl: "https://example.com/avatar.png",
          memberCount: 42,
        },
      },
    ])
  })
})
