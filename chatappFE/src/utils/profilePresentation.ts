import { DEFAULT_PROFILE_BACKGROUND, resolveProfileBackground } from "./profileBackground"

const DEFAULT_DISPLAY_NAME = "Unknown User"
const DEFAULT_USERNAME = "unknown"
const DEFAULT_ABOUT = "No bio yet."
const DEFAULT_AVATAR = "/default-avatar.png"

type ProfilePresentationInput = {
  displayName?: string | null
  username?: string | null
  avatarUrl?: string | null
  aboutMe?: string | null
  backgroundColor?: string | null
}

type ResolveProfilePresentationOptions = {
  fallbackAbout?: boolean
}

export type ProfilePresentation = {
  displayName: string
  username: string
  avatarUrl: string
  aboutMe: string
  backgroundColor: string
}

const normalizeText = (value?: string | null): string => value?.trim() ?? ""

export const resolveProfilePresentation = (
  input: ProfilePresentationInput,
  options: ResolveProfilePresentationOptions = {}
): ProfilePresentation => {
  const displayName = normalizeText(input.displayName) || DEFAULT_DISPLAY_NAME
  const username = normalizeText(input.username) || DEFAULT_USERNAME
  const aboutText = normalizeText(input.aboutMe)
  const aboutMe = aboutText || (options.fallbackAbout === false ? "" : DEFAULT_ABOUT)

  return {
    displayName,
    username,
    aboutMe,
    avatarUrl: normalizeText(input.avatarUrl) || DEFAULT_AVATAR,
    backgroundColor: resolveProfileBackground(
      normalizeText(input.backgroundColor) || DEFAULT_PROFILE_BACKGROUND
    ),
  }
}
