import { useCallback, useEffect, useState } from "react";
import type { RefObject } from "react";

interface UseNewestMessageVisibilityArgs {
  containerRef: RefObject<HTMLDivElement | null>;
  newestSeq: number | null;
}

const isElementVisibleWithinContainer = (
  container: HTMLDivElement,
  element: HTMLElement
) => {
  const containerRect = container.getBoundingClientRect();
  const elementRect = element.getBoundingClientRect();

  // jsdom and some initial layout phases can report zero-sized rects.
  // In that case, fallback to scroll-position math.
  if (containerRect.height <= 0 || elementRect.height <= 0) {
    if (container.clientHeight <= 0) {
      return false;
    }

    const elementTop = element.offsetTop;
    const elementBottom = elementTop + Math.max(element.clientHeight, 1);
    const viewportTop = container.scrollTop;
    const viewportBottom = viewportTop + container.clientHeight;

    return elementBottom >= viewportTop && elementTop <= viewportBottom;
  }

  const verticallyVisible =
    elementRect.bottom >= containerRect.top &&
    elementRect.top <= containerRect.bottom;

  return verticallyVisible;
};

export function useNewestMessageVisibility({
  containerRef,
  newestSeq,
}: UseNewestMessageVisibilityArgs) {
  const [isNewestVisible, setIsNewestVisible] = useState(false);

  const evaluateVisibility = useCallback(() => {
    const container = containerRef.current;

    if (!container || newestSeq == null) {
      setIsNewestVisible(false);
      return;
    }

    const newestElement = container.querySelector<HTMLElement>(
      `[data-message-seq="${newestSeq}"]`
    );

    if (!newestElement) {
      setIsNewestVisible(false);
      return;
    }

    setIsNewestVisible(isElementVisibleWithinContainer(container, newestElement));
  }, [containerRef, newestSeq]);

  useEffect(() => {
    evaluateVisibility();
  }, [evaluateVisibility]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const onScroll = () => {
      evaluateVisibility();
    };

    const onResize = () => {
      evaluateVisibility();
    };

    container.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onResize);

    return () => {
      container.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onResize);
    };
  }, [containerRef, evaluateVisibility]);

  return {
    isNewestVisible,
    evaluateVisibility,
  };
}
