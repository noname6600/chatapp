export interface RecentLoginUser {
  email: string
  displayName: string
  avatarUrl: string | null
}

const STORAGE_KEY = "recent_logins"
const MAX_RECENT = 5

export function saveRecentLogin(user: RecentLoginUser): void {
  const existing = getRecentLogins()
  const filtered = existing.filter((u) => u.email !== user.email)
  const updated = [user, ...filtered].slice(0, MAX_RECENT)
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated))
  } catch {
    // ignore storage quota errors
  }
}

export function getRecentLogins(): RecentLoginUser[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    return JSON.parse(raw) as RecentLoginUser[]
  } catch {
    return []
  }
}

export function removeRecentLogin(email: string): void {
  const updated = getRecentLogins().filter((u) => u.email !== email)
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated))
  } catch {
    // ignore storage quota errors
  }
}
