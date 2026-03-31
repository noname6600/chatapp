const LOCALIZED_COPY: Record<string, { yesterday: string; at: string }> = {
  en: { yesterday: "yesterday", at: "at" },
  es: { yesterday: "ayer", at: "a" },
  fr: { yesterday: "hier", at: "a" },
  de: { yesterday: "gestern", at: "um" },
}

function getLocale(locale?: string) {
  if (locale) {
    return locale
  }

  if (typeof navigator !== "undefined" && navigator.language) {
    return navigator.language
  }

  return "en-US"
}

function getLanguage(locale?: string) {
  return getLocale(locale).split("-")[0].toLowerCase()
}

function getSafeDate(dateString: string) {
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime()) || date.getFullYear() < 2000) {
    return null
  }

  return date
}

function isSameCalendarDay(left: Date, right: Date) {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  )
}

export function isYesterday(dateString: string, now = new Date()) {
  const date = getSafeDate(dateString)
  if (!date) {
    return false
  }

  const yesterday = new Date(now)
  yesterday.setHours(0, 0, 0, 0)
  yesterday.setDate(yesterday.getDate() - 1)

  return isSameCalendarDay(date, yesterday)
}

export function getYesterdayLabel(locale?: string) {
  const copy = LOCALIZED_COPY[getLanguage(locale)] ?? LOCALIZED_COPY.en
  return copy.yesterday
}

export function getAtLabel(locale?: string) {
  const copy = LOCALIZED_COPY[getLanguage(locale)] ?? LOCALIZED_COPY.en
  return copy.at
}

export function formatMessageTimeShort(dateString: string, locale?: string) {
  const date = getSafeDate(dateString)
  if (!date) {
    return new Date().toLocaleTimeString(getLocale(locale), {
      hour: "2-digit",
      minute: "2-digit",
    })
  }

  return date.toLocaleTimeString(getLocale(locale), {
    hour: "numeric",
    minute: "2-digit",
  })
}

export function formatMessageTimestamp(
  dateString: string,
  now = new Date(),
  locale?: string
) {
  const date = getSafeDate(dateString)
  if (!date) {
    return formatMessageTimeShort(new Date().toISOString(), locale)
  }

  const resolvedLocale = getLocale(locale)
  const time = formatMessageTimeShort(dateString, resolvedLocale)

  if (isSameCalendarDay(date, now)) {
    return time
  }

  if (isYesterday(dateString, now)) {
    return `${getYesterdayLabel(resolvedLocale)} ${getAtLabel(resolvedLocale)} ${time}`
  }

  const dateStr = date.toLocaleDateString(resolvedLocale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })

  return `${dateStr} ${time}`
}
