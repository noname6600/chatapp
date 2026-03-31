export const PRESENCE_HEARTBEAT_INTERVAL_MS = 10_000

// Treat users as away only after a meaningful idle period instead of 60s.
export const PRESENCE_AWAY_THRESHOLD_MS = 5 * 60_000

// Short grace period to avoid OFFLINE flicker during refresh/reconnect.
export const PRESENCE_OFFLINE_GRACE_MS = 5_000