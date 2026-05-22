import { describe, expect, it, vi, beforeEach } from "vitest"
import { uploadAvatarApi } from "./user.service"
import { uploadRoomAvatarApi } from "./room.service"

vi.mock("./upload.service", () => ({
  prepareUploadApi: vi.fn(async () => ({
    purpose: "user-avatar",
    cloudName: "demo",
    apiKey: "k",
    uploadUrl: "https://api.cloudinary.com/v1_1/demo/auto/upload",
    timestamp: 1,
    signature: "sig",
    folder: "user/avatar",
    publicId: "user/avatar/id-1",
    maxBytes: 5242880,
    allowedFormats: ["jpg", "png", "webp"],
  })),
  uploadToCloudinarySigned: vi.fn(async () => ({
    public_id: "user/avatar/id-1",
    secure_url: "https://res.cloudinary.com/demo/image/upload/v1/user/avatar/id-1.png",
    resource_type: "image",
    format: "png",
    bytes: 2000,
    width: 256,
    height: 256,
  })),
  confirmUploadApi: vi.fn(async () => ({
    purpose: "user-avatar",
    publicId: "user/avatar/id-1",
    secureUrl: "https://res.cloudinary.com/demo/image/upload/v1/user/avatar/id-1.png",
    resourceType: "image",
    format: "png",
    bytes: 2000,
    width: 256,
    height: 256,
  })),
}))

vi.mock("./user.api", () => ({
  userApi: {
    post: vi.fn(async () => ({
      data: {
        success: true,
        data: {
          avatarUrl: "https://res.cloudinary.com/demo/image/upload/v1/user/avatar/id-1.png",
        },
      },
    })),
  },
}))

vi.mock("./chat.api", () => ({
  chatApi: {
    post: vi.fn(async () => ({
      data: {
        success: true,
        data: {
          url: "https://res.cloudinary.com/demo/image/upload/v1/room_avatars/id-1.png",
        },
      },
    })),
  },
}))

describe("uploadAvatarApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("uses upload-service contract before posting avatar metadata to user-service", async () => {
    const file = new File(["avatar"], "avatar.png", { type: "image/png" })

    const avatarUrl = await uploadAvatarApi(file)

    expect(avatarUrl).toBe("https://res.cloudinary.com/demo/image/upload/v1/user/avatar/id-1.png")
  })

  it("uses confirmed room-avatar metadata before posting to room-service", async () => {
    const file = new File(["avatar"], "room-avatar.png", { type: "image/png" })

    const avatarUrl = await uploadRoomAvatarApi("room-1", file)

    expect(avatarUrl).toBe("https://res.cloudinary.com/demo/image/upload/v1/room_avatars/id-1.png")
  })
})
