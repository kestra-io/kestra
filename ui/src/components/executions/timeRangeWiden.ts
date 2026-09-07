import moment from "moment"
import {FILTER_FIELD_PATTERN} from "./utils"

export const FALLBACK_TIME_RANGE = "PT24H"

// Datepicker chips label PT168H / P30D as 7 / 30 days; P7D would show as the raw ISO string.
export const TIME_RANGE_WIDEN_STEPS = ["PT24H", "PT168H", "P30D"] as const

const TIME_RANGE_EQUALS_KEY = "filters[timeRange][EQUALS]"
const TIME_BOUND_KEY = /startDate|endDate|timeRange/
const ABSOLUTE_DATE_KEY = /startDate|endDate/
const DEFAULT_TIME_RANGE_FIELDS = new Set(["timeRange", "startDate", "endDate", "scope", "namespace"])

export function timeRangeDurationMs(iso: string): number | undefined {
    const ms = moment.duration(iso).asMilliseconds()
    return Number.isFinite(ms) && ms > 0 ? ms : undefined
}

export function isSameTimeRange(a: string, b: string): boolean {
    const aMs = timeRangeDurationMs(a)
    const bMs = timeRangeDurationMs(b)
    if (aMs === undefined || bMs === undefined) {
        return a === b
    }
    return aMs === bMs
}

export function timeRangeWidenSequence(start: string): string[] {
    const startMs = timeRangeDurationMs(start)
    const sequence = [start]
    if (startMs === undefined) {
        return sequence
    }

    for (const step of TIME_RANGE_WIDEN_STEPS) {
        const stepMs = timeRangeDurationMs(step)
        if (stepMs !== undefined && stepMs > startMs && !sequence.some(item => isSameTimeRange(item, step))) {
            sequence.push(step)
        }
    }

    return sequence
}

export function remainingWidenWindows(current: string, defaultTimeRange: string): string[] {
    const sequence = timeRangeWidenSequence(defaultTimeRange)
    const index = sequence.findIndex(step => isSameTimeRange(step, current))
    if (index === -1) {
        return []
    }
    return sequence.slice(index + 1)
}

export function readTimeRangeFromQuery(query: Record<string, unknown>): string | undefined {
    const raw = query[TIME_RANGE_EQUALS_KEY]
    if (typeof raw === "string" && raw.length > 0) {
        return raw
    }
    if (Array.isArray(raw) && typeof raw[0] === "string" && raw[0].length > 0) {
        return raw[0]
    }
    return undefined
}

export function queryHasTimeBound(query: Record<string, unknown>): boolean {
    return Object.keys(query).some(key => TIME_BOUND_KEY.test(key))
}

export function queryHasAbsoluteDateFilter(query: Record<string, unknown>): boolean {
    return Object.keys(query).some(key => ABSOLUTE_DATE_KEY.test(key))
}

export function queryHasUserFilters(query: Record<string, unknown>): boolean {
    return Object.keys(query).some((key) => {
        const field = key.match(FILTER_FIELD_PATTERN)?.[1]
        return field !== undefined && !DEFAULT_TIME_RANGE_FIELDS.has(field)
    })
}

export async function widenEmptyTimeRange(options: {
    currentTimeRange: string | undefined;
    defaultTimeRange: string;
    hasAbsoluteDateFilter: boolean;
    alreadyAttempted: boolean;
    hasUserFilters: boolean;
    currentTotal: number;
    search: (timeRange: string) => Promise<number>;
}): Promise<{timeRange: string | undefined; widened: boolean}> {
    const {
        currentTimeRange,
        defaultTimeRange,
        hasAbsoluteDateFilter,
        alreadyAttempted,
        hasUserFilters,
        currentTotal,
        search,
    } = options

    if (alreadyAttempted || hasAbsoluteDateFilter || hasUserFilters || currentTotal > 0) {
        return {timeRange: currentTimeRange, widened: false}
    }

    const current = currentTimeRange ?? defaultTimeRange
    const remaining = remainingWidenWindows(current, defaultTimeRange)

    let applied = currentTimeRange
    let widened = false
    for (const window of remaining) {
        const total = await search(window)
        applied = window
        widened = true
        if (total > 0) {
            break
        }
    }

    return {timeRange: applied, widened}
}
