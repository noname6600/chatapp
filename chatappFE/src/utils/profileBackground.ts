export const DEFAULT_PROFILE_BACKGROUND = "#6366f1"

const HEX_COLOR_PATTERN = /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/

export const resolveProfileBackground = (
  backgroundColor?: string | null
): string => {
  const candidate = backgroundColor?.trim() ?? ""

  if (!candidate) {
    return DEFAULT_PROFILE_BACKGROUND
  }

  if (!HEX_COLOR_PATTERN.test(candidate)) {
    return DEFAULT_PROFILE_BACKGROUND
  }

  return candidate
}