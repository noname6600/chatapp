type AnalyticsPayload = Record<string, unknown>

declare global {
  interface Window {
    dataLayer?: Array<Record<string, unknown>>
  }
}

/**
 * Emits analytics events in a provider-agnostic way.
 * - Pushes into GTM dataLayer when available
 * - Also emits a browser custom event for local observers
 */
export function trackEvent(eventName: string, payload: AnalyticsPayload = {}) {
  if (typeof window === "undefined") {
    return
  }

  if (Array.isArray(window.dataLayer)) {
    window.dataLayer.push({ event: eventName, ...payload })
  }

  window.dispatchEvent(
    new CustomEvent("chatapp:analytics", {
      detail: {
        event: eventName,
        ...payload,
      },
    })
  )
}
