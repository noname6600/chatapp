import { describe, expect, it } from "vitest"
import axios from "axios"
import { extractErrorCode, extractErrorMessage, extractErrorStatus } from "./error"

describe("error utils", () => {
  it("extracts nested ApiResponse error message", () => {
    const error = new axios.AxiosError("Request failed")
    ;(error as any).response = {
      status: 409,
      data: {
        success: false,
        error: {
          code: "INCOMPLETE_ACCOUNT",
          message: "Account setup incomplete. Please try again in a few seconds.",
        },
      },
    }

    expect(extractErrorMessage(error)).toBe("Account setup incomplete. Please try again in a few seconds.")
    expect(extractErrorCode(error)).toBe("INCOMPLETE_ACCOUNT")
    expect(extractErrorStatus(error)).toBe(409)
  })

  it("falls back to top-level message when error envelope is missing", () => {
    const error = new axios.AxiosError("Network Error")
    ;(error as any).response = {
      status: 401,
      data: {
        message: "Invalid credentials",
      },
    }

    expect(extractErrorMessage(error)).toBe("Invalid credentials")
    expect(extractErrorCode(error)).toBeNull()
    expect(extractErrorStatus(error)).toBe(401)
  })
})
