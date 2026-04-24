// @vitest-environment jsdom

import { describe, expect, it } from "vitest"
import { render } from "@testing-library/react"

import TypingDots from "./TypingDots"

describe("TypingDots", () => {
  it("renders three animated dots", () => {
    const { container } = render(<TypingDots />)

    const dots = document.querySelectorAll(".typing-dot")
    expect(dots.length).toBe(3)
    expect(container.querySelector(".typing-dots")).not.toBeNull()
  })
})
