import axios from "axios"

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | { message?: string; error?: { message?: string } }
      | undefined
    return data?.error?.message ?? data?.message ?? error.message
  }
  if (error instanceof Error) return error.message
  return String(error)
}

export function extractErrorCode(error: unknown): string | null {
  if (!axios.isAxiosError(error)) return null
  const data = error.response?.data as
    | { code?: string; error?: { code?: string } }
    | undefined
  return data?.error?.code ?? data?.code ?? null
}

export function extractErrorStatus(error: unknown): number | null {
  if (!axios.isAxiosError(error)) return null
  return error.response?.status ?? null
}
