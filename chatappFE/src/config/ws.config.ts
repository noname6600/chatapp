/**
 * WebSocket Gateway Configuration
 * All WebSocket connections route through the gateway on a single host/port.
 * 
 * Service WebSocket endpoints (via gateway):
 * - /ws/chat → chat real-time messages
 * - /ws/presence → user presence (online/offline)
 * - /ws/friendship → friendship notifications
 * - /ws/notifications → general notifications
 * 
 * Environment variable: VITE_WS_URL
 * Default: ws://localhost:8080 (local development, no path suffix)
 * Production: wss://api.example.com (use wss:// for secure WebSocket)
 * 
 * Note: WebSocket paths are NOT /api/v1/ like REST endpoints.
 * The gateway routes /ws/{service}/** directly to backend WebSocket handlers.
 */
const WS_GATEWAY_BASE = import.meta.env.VITE_WS_URL || "ws://localhost:8080";

export const WS_ENDPOINTS = {
  CHAT: `${WS_GATEWAY_BASE}/ws/chat`,
  PRESENCE: `${WS_GATEWAY_BASE}/ws/presence`,
  FRIEND: `${WS_GATEWAY_BASE}/ws/friendship`,
  NOTIFICATION: `${WS_GATEWAY_BASE}/ws/notifications`,
};

/**
 * Get WebSocket endpoint URL for a given service
 * @param service - Service name (chat, presence, friend, notification)
 * @returns Full WebSocket URL for the service
 */
export const getWsEndpoint = (
  service: keyof typeof WS_ENDPOINTS
): string => {
  return WS_ENDPOINTS[service];
};
