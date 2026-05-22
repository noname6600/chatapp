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

// Converts a raw timestamp (ISO string or Unix seconds as number/string) to ms.
// Threshold 1e12: values below it are seconds (current epoch ~1.78e9 s), above are already ms.
function toMs(dateInput: number | string): number {
  if (typeof dateInput === "number") {
    return dateInput < 1_000_000_000_000 ? dateInput * 1000 : dateInput
  }
  const asNumber = Number(dateInput)
  if (!Number.isNaN(asNumber) && Number.isFinite(asNumber)) {
    return asNumber < 1_000_000_000_000 ? asNumber * 1000 : asNumber
  }
  return new Date(dateInput).getTime()
}

export function getSafeDate(dateInput: number | string | null | undefined): Date | null {
  if (dateInput == null) return null
  const ms = toMs(dateInput)
  const date = new Date(ms)
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

export function isYesterday(dateInput: number | string, now = new Date()) {
  const date = getSafeDate(dateInput)
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

export function formatMessageTimeShort(dateInput: number | string, locale?: string) {
  const date = getSafeDate(dateInput)
  if (!date) {
    return "--:--"
  }

  return date.toLocaleTimeString(getLocale(locale), {
    hour: "numeric",
    minute: "2-digit",
  })
}

export function formatMessageTimestamp(
  dateInput: number | string,
  now = new Date(),
  locale?: string
) {
  const date = getSafeDate(dateInput)
  if (!date) {
    return "--:--"
  }

  const resolvedLocale = getLocale(locale)
  const time = formatMessageTimeShort(dateInput, resolvedLocale)

  if (isSameCalendarDay(date, now)) {
    return time
  }

  if (isYesterday(dateInput, now)) {
    return `${getYesterdayLabel(resolvedLocale)} ${getAtLabel(resolvedLocale)} ${time}`
  }

  const dateStr = date.toLocaleDateString(resolvedLocale, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })

  return `${dateStr} ${time}`
}
