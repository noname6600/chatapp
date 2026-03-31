/**
 * Scroll utility helpers for message list auto-scroll behavior
 */

const SCROLL_THRESHOLD = 50; // pixels from bottom to consider "at bottom"
const SCROLL_BATCH_WINDOW = 100; // milliseconds to batch scroll triggers

let batchedScrollTimeout: ReturnType<typeof setTimeout> | null = null;
let pendingScroll: (() => void) | null = null;

/**
 * Determines if a scroll container is at or near the bottom
 * @param scrollContainer The DOM element with scrollable content
 * @returns true if user is within SCROLL_THRESHOLD pixels of the bottom
 */
export function isAtBottom(scrollContainer: HTMLElement | null): boolean {
  if (!scrollContainer) return false;
  
  const { scrollTop, clientHeight, scrollHeight } = scrollContainer;
  return scrollTop + clientHeight >= scrollHeight - SCROLL_THRESHOLD;
}

/**
 * Smoothly scrolls a container to the bottom
 * @param scrollContainer The DOM element to scroll
 */
export function scrollToBottom(scrollContainer: HTMLElement | null): void {
  if (!scrollContainer) return;
  
  // Check if user prefers reduced motion
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const behavior = prefersReducedMotion ? "auto" : "smooth";
  
  scrollContainer.scrollTo({
    top: scrollContainer.scrollHeight,
    behavior,
  });
}

/**
 * Batches multiple scroll-to-bottom requests into a single scroll operation
 * within SCROLL_BATCH_WINDOW milliseconds to prevent animation stutter
 * @param scrollContainer The DOM element to scroll
 */
export function batchScrollToBottom(scrollContainer: HTMLElement | null): void {
  if (!scrollContainer) return;
  
  // Store the scroll action
  pendingScroll = () => scrollToBottom(scrollContainer);
  
  // Clear existing timeout
  if (batchedScrollTimeout !== null) {
    clearTimeout(batchedScrollTimeout);
  }
  
  // Schedule batched scroll for next batch window
  batchedScrollTimeout = setTimeout(() => {
    if (pendingScroll) {
      pendingScroll();
      pendingScroll = null;
    }
    batchedScrollTimeout = null;
  }, SCROLL_BATCH_WINDOW);
}

/**
 * Get the scroll threshold value
 * @returns The pixel threshold from bottom for "at bottom" detection
 */
export function getScrollThreshold(): number {
  return SCROLL_THRESHOLD;
}
