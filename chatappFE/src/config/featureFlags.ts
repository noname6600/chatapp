/**
 * Feature flags for controlling new chat behavior features
 * These can be toggled via environment variables or local storage
 */

export const FEATURE_FLAGS = {
  /**
   * Enable auto-scroll-to-bottom when new messages arrive
   * If false, message list scroll behavior is unchanged
   */
  enableAutoScrollOnNewMessage:
    (import.meta.env.VITE_ENABLE_AUTO_SCROLL ?? "true") === "true",

  /**
   * Enable self-message filtering from unread counts
   * If false, messages sent by current user count as unread (legacy behavior)
   */
  enableSelfMessageUnreadExclusion:
    (import.meta.env.VITE_ENABLE_SELF_MESSAGE_UNREAD_EXCLUSION ?? "true") === "true",
};

/**
 * Check if a feature flag is enabled
 * Allows runtime toggling via local storage override
 */
export function isFeatureEnabled(flagName: keyof typeof FEATURE_FLAGS): boolean {
  // Check local storage override first (dev/testing)
  const override = localStorage.getItem(`feature_flag_${flagName}`);
  if (override !== null) {
    return override === "true";
  }

  return FEATURE_FLAGS[flagName];
}

/**
 * Enable a feature flag temporarily (for testing/debugging)
 */
export function setFeatureFlag(flagName: keyof typeof FEATURE_FLAGS, enabled: boolean): void {
  localStorage.setItem(`feature_flag_${flagName}`, String(enabled));
}

/**
 * Clear all feature flag overrides
 */
export function clearFeatureFlagOverrides(): void {
  Object.keys(FEATURE_FLAGS).forEach((flagName) => {
    localStorage.removeItem(`feature_flag_${flagName}`);
  });
}
