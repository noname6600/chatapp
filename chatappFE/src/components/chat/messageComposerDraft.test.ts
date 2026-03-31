import { describe, expect, it, vi, beforeEach } from "vitest"

import {
  appendTextBlock,
  buildMessageBlocks,
  createUploadingAssetPlaceholders,
} from "./messageComposerDraft"
import { extractClipboardFiles } from "./messageInput.behavior"

describe("message composer draft helpers", () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it("extracts file items from clipboard-like input", () => {
    const image = new File(["img"], "paste.png", { type: "image/png" })

    const files = extractClipboardFiles([
      {
        kind: "string",
        getAsFile: () => null,
      },
      {
        kind: "file",
        getAsFile: () => image,
      },
    ])

    expect(files).toHaveLength(1)
    expect(files[0]?.name).toBe("paste.png")
  })

  it("flushes trailing text before inserted asset placeholders for draft preview order", () => {
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:preview")

    const blocks = appendTextBlock([], "Before")
    const placeholders = createUploadingAssetPlaceholders(
      [],
      "",
      [new File(["img"], "photo.png", { type: "image/png" })]
    )

    const orderedDraft = [...blocks, ...placeholders]

    expect(orderedDraft).toHaveLength(2)
    expect(orderedDraft[0]).toMatchObject({ type: "TEXT", text: "Before" })
    expect(orderedDraft[1]).toMatchObject({
      type: "ASSET",
      status: "uploading",
      fileName: "photo.png",
      previewUrl: "blob:preview",
    })
  })

  it("builds mixed message payload in authored text-asset-text order", () => {
    const blocks = [
      ...appendTextBlock([], "Before"),
      {
        id: "asset-1",
        type: "ASSET" as const,
        status: "ready" as const,
        fileName: "photo.png",
        attachment: {
          type: "IMAGE" as const,
          url: "https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/photo.png",
          publicId: "chat/attachments/photo",
          fileName: "photo.png",
        },
      },
      ...appendTextBlock([], "After"),
    ]

    expect(buildMessageBlocks(blocks, "")).toEqual([
      { type: "TEXT", text: "Before" },
      {
        type: "ASSET",
        attachment: expect.objectContaining({
          type: "IMAGE",
          publicId: "chat/attachments/photo",
        }),
      },
      { type: "TEXT", text: "After" },
    ])
  })
})