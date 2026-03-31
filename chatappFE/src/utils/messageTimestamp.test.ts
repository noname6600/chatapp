import { describe, expect, it } from "vitest"

import {
  formatMessageTimestamp,
  getYesterdayLabel,
  isYesterday,
} from "./messageTimestamp"

describe("messageTimestamp", () => {
  it("detects yesterday using calendar boundaries", () => {
    expect(
      isYesterday(
        "2026-03-19T23:30:00",
        new Date("2026-03-20T00:15:00")
      )
    ).toBe(true)
  })

  it("does not treat same-day timestamps as yesterday", () => {
    expect(
      isYesterday(
        "2026-03-20T00:05:00",
        new Date("2026-03-20T23:59:00")
      )
    ).toBe(false)
  })

  it("formats yesterday messages in english with localized time", () => {
    expect(
      formatMessageTimestamp(
        "2026-03-19T14:30:00",
        new Date("2026-03-20T08:00:00"),
        "en-US"
      )
    ).toBe("yesterday at 2:30 PM")
  })

  it("formats yesterday messages in spanish with localized label", () => {
    expect(
      formatMessageTimestamp(
        "2026-03-19T14:30:00",
        new Date("2026-03-20T08:00:00"),
        "es-ES"
      )
    ).toBe("ayer a 14:30")
  })

  it("keeps same-day messages as time only", () => {
    expect(
      formatMessageTimestamp(
        "2026-03-20T09:05:00",
        new Date("2026-03-20T18:00:00"),
        "en-US"
      )
    ).toBe("9:05 AM")
  })

  it("keeps older messages as date plus time", () => {
    expect(
      formatMessageTimestamp(
        "2026-03-18T14:30:00",
        new Date("2026-03-20T08:00:00"),
        "en-US"
      )
    ).toContain("03/18/2026")
  })

  it("falls back to english yesterday label for unsupported locales", () => {
    expect(getYesterdayLabel("vi-VN")).toBe("yesterday")
  })
})
