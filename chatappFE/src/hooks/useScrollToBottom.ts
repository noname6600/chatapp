import { useCallback, useRef } from "react";
import { isAtBottom, batchScrollToBottom } from "../utils/scrollUtils";

interface UseScrollToBottomOptions {
  enabled?: boolean;
  onlyIfAtBottom?: boolean;
}

/**
 * Custom hook for managing smooth scroll-to-bottom behavior
 * Provides utilities for checking if user is at bottom and scrolling when needed
 */
export function useScrollToBottom(options: UseScrollToBottomOptions = {}) {
  const { enabled = true, onlyIfAtBottom = true } = options;
  
  const containerRef = useRef<HTMLDivElement>(null);
  
  /**
   * Check if the scroll container is at the bottom
   */
  const checkAtBottom = useCallback((): boolean => {
    return isAtBottom(containerRef.current);
  }, []);
  
  /**
   * Scroll to bottom with optional conditional logic
   * @param force If true, scroll even if not at bottom; if false, check onlyIfAtBottom setting
   */
  const scrollToBottom = useCallback((force: boolean = false) => {
    if (!enabled || !containerRef.current) return;
    
    // If onlyIfAtBottom is true and force is false, only scroll if already at bottom
    if (onlyIfAtBottom && !force && !checkAtBottom()) {
      return;
    }
    
    batchScrollToBottom(containerRef.current);
  }, [enabled, onlyIfAtBottom, checkAtBottom]);
  
  /**
   * Force scroll to bottom regardless of current position
   */
  const forceScrollToBottom = useCallback(() => {
    scrollToBottom(true);
  }, [scrollToBottom]);
  
  return {
    containerRef,
    checkAtBottom,
    scrollToBottom,
    forceScrollToBottom,
  };
}
