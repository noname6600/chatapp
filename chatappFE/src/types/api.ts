export interface ApiError {
  code: string
  message: string
  details?: unknown
}

export interface ApiResponse<T> {
  success: boolean
  timestamp: string
  data: T | null
  error: ApiError | null
  traceId: string
}