import dayjs, {type Dayjs} from "../date/dayjs"

export const DATE_FORMAT_STORAGE_KEY = "dateFormat"
export const TIMEZONE_STORAGE_KEY = "timezone"

const ISO_8601 = /^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?)?$/

// "en" ships inside dayjs; every other locale is a separate chunk fetched only when selected.
const LOCALE_LOADERS: Record<string, () => Promise<unknown>> = {
    de: () => import("dayjs/locale/de"),
    es: () => import("dayjs/locale/es"),
    fr: () => import("dayjs/locale/fr"),
    hi: () => import("dayjs/locale/hi"),
    it: () => import("dayjs/locale/it"),
    ja: () => import("dayjs/locale/ja"),
    ko: () => import("dayjs/locale/ko"),
    pl: () => import("dayjs/locale/pl"),
    pt: () => import("dayjs/locale/pt"),
    "pt-br": () => import("dayjs/locale/pt-br"),
    ru: () => import("dayjs/locale/ru"),
    "zh-cn": () => import("dayjs/locale/zh-cn"),
}

/** Loads and activates a language, normalising Kestra's codes ("pt_BR") to dayjs's ("pt-br"). */
export async function setLocale(lang: string): Promise<void> {
    const key = lang.toLowerCase().replace(/_/g, "-")
    const loader = LOCALE_LOADERS[key]
    if (!loader) {
        dayjs.locale("en")
        return
    }
    await loader()
    dayjs.locale(key)
}

export function currentLocale(): string {
    return dayjs.locale()
}

export function currentTimezone(): string {
    return localStorage.getItem(TIMEZONE_STORAGE_KEY) || dayjs.tz.guess()
}

export interface TimezoneOffset {
    zone: string
    /** Minutes east of UTC, as of now. */
    offset: number
    /** The same offset as "+09:00". */
    formattedOffset: string
}

let timezonesCache: TimezoneOffset[] | undefined

/**
 * Every IANA zone with its current offset, sorted west to east. Memoised for the session: each
 * entry costs one `Intl.DateTimeFormat`, and there are over four hundred of them.
 */
export function timezonesWithOffset(): TimezoneOffset[] {
    timezonesCache ??= Intl.supportedValuesOf("timeZone")
        .map((zone) => {
            const inZone = dayjs().tz(zone)
            return {zone, offset: inZone.utcOffset(), formattedOffset: inZone.format("Z")}
        })
        .sort((a, b) => a.offset - b.offset)

    return timezonesCache
}

/**
 * Parses strictly-ISO-8601 text, so anything else (an epoch number, a bare year, a label) comes
 * back invalid instead of being coerced into a date.
 */
export function parseIso(value: unknown): Dayjs {
    return typeof value === "string" && ISO_8601.test(value) ? dayjs(value) : dayjs(NaN)
}

/** ISO 8601 keeping the instance's own UTC offset, where `toISOString()` would convert to UTC. */
export function toIsoKeepOffset(date: Dayjs): string {
    return date.format("YYYY-MM-DDTHH:mm:ss.SSSZ")
}

/**
 * Formats an instant in the timezone and date format from Settings.
 *
 * Callers must not pre-serialise a timestamp with `toISOString()` — that throws on a non-finite
 * value, whereas this degrades to "Invalid Date".
 *
 * @param format a dayjs format, or "iso" for `YYYY-MM-DD HH:mm:ss.SSS`; defaults to the stored one
 */
export const dateFilter = (dateString: string | number | Date, format?: string): string => {
    const f = format === "iso"
        ? "YYYY-MM-DD HH:mm:ss.SSS"
        : format ?? localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "llll"

    return dayjs(dateString).tz(currentTimezone()).format(f)
}
