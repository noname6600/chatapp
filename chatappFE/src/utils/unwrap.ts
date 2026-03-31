import type { AxiosResponse } from "axios"
import type { ApiResponse } from "../types/api"

export function unwrap<T>(res: AxiosResponse<ApiResponse<T>>): T {
  const body = res.data

  if (!body.success || body.error) {
    throw new Error(body.error?.message || "API error")
  }

  if (body.data === null) {
    throw new Error("No data returned")
  }

  return body.data
}
