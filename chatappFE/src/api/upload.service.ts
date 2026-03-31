import { uploadApi } from "./upload.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorMessage } from "../utils/error"
import type { ApiResponse } from "../types/api"
import type { Attachment } from "../types/message"

export type UploadPurpose = "chat-attachment" | "user-avatar"

export type PrepareUploadResponse = {
  purpose: UploadPurpose
  cloudName: string
  apiKey: string
  uploadUrl: string
  timestamp: number
  signature: string
  folder: string
  publicId: string
  maxBytes: number
  allowedFormats: string[]
}

export type ConfirmUploadRequest = {
  purpose: UploadPurpose
  publicId: string
  secureUrl: string
  resourceType: string
  format: string
  bytes: number
  width?: number
  height?: number
  duration?: number
  originalFilename?: string
}

export type UploadAssetResponse = {
  purpose: UploadPurpose
  publicId: string
  secureUrl: string
  resourceType: string
  format: string
  bytes: number
  width?: number
  height?: number
  duration?: number
  originalFilename?: string
}

type CloudinaryUploadRaw = {
  public_id: string
  secure_url: string
  resource_type: string
  format: string
  bytes: number
  width?: number
  height?: number
  duration?: number
  original_filename?: string
}

export const prepareUploadApi = async (
  purpose: UploadPurpose,
  fileName?: string
): Promise<PrepareUploadResponse> => {
  try {
    const res = await uploadApi.post<ApiResponse<PrepareUploadResponse>>(
      "/uploads/prepare",
      { purpose, fileName }
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const confirmUploadApi = async (
  payload: ConfirmUploadRequest
): Promise<UploadAssetResponse> => {
  try {
    const res = await uploadApi.post<ApiResponse<UploadAssetResponse>>(
      "/uploads/confirm",
      payload
    )

    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const uploadToCloudinarySigned = async (
  file: File,
  prepare: PrepareUploadResponse
): Promise<CloudinaryUploadRaw> => {
  const formData = new FormData()
  formData.append("file", file)
  formData.append("api_key", prepare.apiKey)
  formData.append("timestamp", String(prepare.timestamp))
  formData.append("signature", prepare.signature)
  formData.append("folder", prepare.folder)
  formData.append("public_id", prepare.publicId)

  const response = await fetch(prepare.uploadUrl, {
    method: "POST",
    body: formData,
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || "Cloudinary upload failed")
  }

  return (await response.json()) as CloudinaryUploadRaw
}

export const uploadChatAttachment = async (file: File): Promise<Attachment> => {
  const prepared = await prepareUploadApi("chat-attachment", file.name)
  const uploaded = await uploadToCloudinarySigned(file, prepared)

  const confirmed = await confirmUploadApi({
    purpose: "chat-attachment",
    publicId: uploaded.public_id,
    secureUrl: uploaded.secure_url,
    resourceType: uploaded.resource_type,
    format: uploaded.format,
    bytes: uploaded.bytes,
    width: uploaded.width,
    height: uploaded.height,
    duration:
      typeof uploaded.duration === "number"
        ? Math.round(uploaded.duration)
        : undefined,
    originalFilename: uploaded.original_filename ?? file.name,
  })

  return {
    type:
      confirmed.resourceType === "image"
        ? "IMAGE"
        : confirmed.resourceType === "video"
          ? "VIDEO"
          : "FILE",
    url: confirmed.secureUrl,
    publicId: confirmed.publicId,
    fileName: confirmed.originalFilename ?? file.name,
    name: confirmed.originalFilename ?? file.name,
    size: confirmed.bytes,
    width: confirmed.width,
    height: confirmed.height,
    duration: confirmed.duration,
    format: confirmed.format,
    resourceType: confirmed.resourceType,
  }
}
