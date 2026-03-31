export enum PresenceEventType {
  USER_ONLINE = "presence.user.online",
  USER_OFFLINE = "presence.user.offline",
  USER_STATUS_CHANGED = "presence.user.status_changed",
  USER_HEARTBEAT = "presence.user.heartbeat",

  ROOM_JOIN = "presence.room.join",
  ROOM_LEAVE = "presence.room.leave",

  USER_TYPING = "presence.room.typing",
  USER_STOP_TYPING = "presence.room.stop_typing",

  ROOM_ONLINE_USERS = "presence.room.online_users",

  GLOBAL_SNAPSHOT = "presence.global.snapshot",
  ROOM_SNAPSHOT = "presence.room.snapshot",
}
