import { describe, expect, it } from "vitest"

import { resolveProfilePresentation } from "./profilePresentation"

describe("resolveProfilePresentation", () => {
  it("applies deterministic fallbacks for missing identity fields", () => {
    const presentation = resolveProfilePresentation({
      displayName: "",
      username: "",
      avatarUrl: null,
      aboutMe: "",
      backgroundColor: "not-a-color",
    })

    expect(presentation.displayName).toBe("Unknown User")
    expect(presentation.username).toBe("unknown")
    expect(presentation.avatarUrl).toBe("/default-avatar.png")
    expect(presentation.aboutMe).toBe("No bio yet.")
    expect(presentation.backgroundColor).toBe("#6366f1")
  })

  it("preserves valid provided values", () => {
    const presentation = resolveProfilePresentation({
      displayName: "Alex",
      username: "alex_01",
      avatarUrl: "https://img.test/avatar.png",
      aboutMe: "hello there",
      backgroundColor: "#123abc",
    })

    expect(presentation.displayName).toBe("Alex")
    expect(presentation.username).toBe("alex_01")
    expect(presentation.avatarUrl).toBe("https://img.test/avatar.png")
    expect(presentation.aboutMe).toBe("hello there")
    expect(presentation.backgroundColor).toBe("#123abc")
  })
})
