// @vitest-environment jsdom

import { describe, expect, it, vi } from "vitest";

import { batchScrollToBottom, isAtBottom, scrollToBottom } from "./scrollUtils";

describe("scrollUtils", () => {
  it("detects when container is near bottom", () => {
    const element = {
      scrollTop: 450,
      clientHeight: 500,
      scrollHeight: 990,
    } as HTMLElement;

    expect(isAtBottom(element)).toBe(true);
  });

  it("detects when container is not at bottom", () => {
    const element = {
      scrollTop: 100,
      clientHeight: 300,
      scrollHeight: 1000,
    } as HTMLElement;

    expect(isAtBottom(element)).toBe(false);
  });

  it("scrolls with smooth behavior when reduced motion is off", () => {
    const scrollTo = vi.fn();
    const matchMedia = vi
      .spyOn(window, "matchMedia")
      .mockReturnValue({ matches: false } as MediaQueryList);

    const element = {
      scrollHeight: 1000,
      scrollTo,
    } as unknown as HTMLElement;

    scrollToBottom(element);

    expect(scrollTo).toHaveBeenCalledWith({
      top: 1000,
      behavior: "smooth",
    });

    matchMedia.mockRestore();
  });

  it("batches rapid scroll calls into one call", async () => {
    vi.useFakeTimers();

    const scrollTo = vi.fn();
    const element = {
      scrollHeight: 1000,
      scrollTo,
    } as unknown as HTMLElement;

    const matchMedia = vi
      .spyOn(window, "matchMedia")
      .mockReturnValue({ matches: false } as MediaQueryList);

    batchScrollToBottom(element);
    batchScrollToBottom(element);
    batchScrollToBottom(element);

    expect(scrollTo).not.toHaveBeenCalled();

    vi.advanceTimersByTime(110);

    expect(scrollTo).toHaveBeenCalledTimes(1);

    matchMedia.mockRestore();
    vi.useRealTimers();
  });
});
