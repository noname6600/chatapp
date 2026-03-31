// @vitest-environment jsdom

import { describe, expect, it, vi, beforeEach } from "vitest"
import { act, renderHook } from "@testing-library/react"
import { useFileUpload } from "./useFileUpload"
import type { Attachment } from "../types/message"

vi.mock("../api/upload.service", () => ({
  uploadChatAttachment: vi.fn(async () => ({
    type: "IMAGE",
    url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/id-1.jpg",
    publicId: "chat/attachments/id-1",
    fileName: "photo.jpg",
    name: "photo.jpg",
    size: 1234,
    width: 400,
    height: 300,
    format: "jpg",
    resourceType: "image",
  })),
}))

describe("useFileUpload", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("uploads chat attachments through prepare -> upload -> confirm contract", async () => {
    const { result } = renderHook(() => useFileUpload())

    const file = new File(["x"], "photo.jpg", { type: "image/jpeg" })

    act(() => {
      result.current.addFiles([file])
    })

    let uploaded: Attachment[] | null = null

    await act(async () => {
      uploaded = await result.current.uploadFiles()
    })

    expect(uploaded).not.toBeNull()
    if (!uploaded) {
      throw new Error("Expected uploaded attachments")
    }

    const uploadedAttachments: Attachment[] = uploaded

    expect(uploadedAttachments).toHaveLength(1)
    expect(uploadedAttachments[0]?.publicId).toBe("chat/attachments/id-1")
    expect(uploadedAttachments[0]?.type).toBe("IMAGE")
    expect(result.current.error).toBeNull()
  })
})
