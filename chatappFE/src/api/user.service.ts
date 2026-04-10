import { userApi } from "./user.api"
import { unwrap } from "../utils/unwrap"
import { extractErrorCode, extractErrorMessage, extractErrorStatus } from "../utils/error"
import {
  confirmUploadApi,
  prepareUploadApi,
  uploadToCloudinarySigned,
} from "./upload.service"
import type { ApiResponse } from "../types/api"
import type { UserProfile, UpdateProfileRequest } from "../types/user"

const MENTION_DEBUG = import.meta.env.DEV

const normalizeBulkProfile = (raw: UserProfile | Record<string, unknown>): UserProfile => {
  const value = raw as Record<string, unknown>
  const accountId = String(value.accountId ?? "")
  const username = String(value.username ?? value.userName ?? "").trim()
  const displayName = String(value.displayName ?? value.name ?? username).trim()

  return {
    accountId,
    username,
    displayName,
    avatarUrl: (value.avatarUrl as string | null | undefined) ?? null,
    aboutMe: (value.aboutMe as string | null | undefined) ?? null,
    backgroundColor: (value.backgroundColor as string | null | undefined) ?? null,
  }
}

// =========================
// GET
// =========================

export const getMyProfileApi = async (): Promise<UserProfile> => {
  try {
    const res = await userApi.get<ApiResponse<UserProfile>>("/me")
    return unwrap(res)
  } catch (error) {
    const wrapped = new Error(extractErrorMessage(error)) as Error & {
      code?: string
      status?: number
    }
    wrapped.code = extractErrorCode(error) ?? undefined
    wrapped.status = extractErrorStatus(error) ?? undefined
    throw wrapped
  }
}

export const getUserByIdApi = async (id: string): Promise<UserProfile> => {
  try {
    const res = await userApi.get<ApiResponse<UserProfile>>(`/${id}`)
    return unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const getUsersBulkApi = async (ids: string[]): Promise<UserProfile[]> => {
  try {
    if (MENTION_DEBUG) {
      console.log("[mention-debug] getUsersBulkApi request", { ids, count: ids.length })
    }

    const res = await userApi.post<ApiResponse<UserProfile[]>>("/bulk", ids)

    const data = unwrap(res).map((item) =>
      normalizeBulkProfile(item as UserProfile | Record<string, unknown>)
    )

    if (MENTION_DEBUG) {
      data.forEach((u) => {
        console.log(
          `[mention-debug] getUsersBulkApi response id=${u.accountId} username=${u.username || "<empty>"} displayName=${u.displayName || "<empty>"}`
        )
      })
    }

    return data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const searchUserByUsernameApi = async (
  username: string
): Promise<UserProfile | null> => {
  try {
    const res = await userApi.get<ApiResponse<UserProfile>>(
      `/search?username=${encodeURIComponent(username)}`
    )
    return unwrap(res)
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } })?.response?.status
    if (status === 404) {
      return null
    }
    throw new Error(extractErrorMessage(error))
  }
}

// =========================
// PATCH / POST
// =========================

export const updateMyProfileApi = async (
  payload: UpdateProfileRequest
): Promise<void> => {
  try {
    const res = await userApi.patch<ApiResponse<null>>("/me", payload)
    unwrap(res)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export const uploadAvatarApi = async (file: File): Promise<string> => {
  try {
    const prepared = await prepareUploadApi("user-avatar", file.name)
    const uploaded = await uploadToCloudinarySigned(file, prepared)
    const confirmed = await confirmUploadApi({
      purpose: "user-avatar",
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

    const res = await userApi.post<ApiResponse<{ avatarUrl: string }>>(
      "/me/avatar",
      {
        publicId: confirmed.publicId,
        secureUrl: confirmed.secureUrl,
        resourceType: confirmed.resourceType,
        format: confirmed.format,
        bytes: confirmed.bytes,
        width: confirmed.width,
        height: confirmed.height,
      }
    )

    const data = unwrap(res)
    return data.avatarUrl
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
