/**
 * API Gateway Configuration
 * All REST API requests route through the gateway on a single port.
 * 
 * The gateway handles path-based routing:
 * - /api/v1/auth/* → auth-service
 * - /api/v1/user/* → user-service
 * - /api/v1/chat/* → chat-service
 * - etc.
 * 
 * Environment variable: VITE_API_URL
 * Default: http://localhost:8080/api/v1 (local development)
 */
export const API_GATEWAY_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1";

const stripTrailingSlash = (value: string) => value.replace(/\/+$/, "")
const buildApiUrl = (segment: string) => `${stripTrailingSlash(API_GATEWAY_URL)}/${segment}`

export const API_URL = {
  AUTH: buildApiUrl("auth"),
  CHAT: buildApiUrl("chat"),
  PRESENCE: buildApiUrl("presence"),
  NOTIFICATION: buildApiUrl("notifications"),
  USER: buildApiUrl("users"),
  UPLOAD: buildApiUrl("upload"),
  FRIEND: buildApiUrl("friendship"),
  CHAT_INSTANCE: buildApiUrl("chat"),
};