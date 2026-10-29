import type {LocationQuery, LocationQueryRaw} from "vue-router"
import {applyDefaultFilters, dayjs, normalizeRouteTimeRangeFilter} from "@kestra-io/design-system"

export type DateRange = {startMs: number; endMs: number}
export type BrushRange = {startDate: string; endDate: string}

export const BRUSH_START_KEY = "filters[startDate][GREATER_THAN_OR_EQUAL_TO]"
export const BRUSH_END_KEY = "filters[endDate][LESS_THAN_OR_EQUAL_TO]"

const TIME_RANGE_EQUALS_KEY = "filters[timeRange][EQUALS]"
const ABSOLUTE_DATE_KEY_PREFIXES = ["filters[startDate]", "filters[endDate]"]

const BUCKET_LABEL_PATTERNS: Record<string, RegExp> = {
    day: /^(\d{4})-(\d{2})-(\d{2})$/,
    hour: /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):00$/,
    minute: /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})$/,
}

const FIXED_BUCKET_MS: Record<string, number> = {
    hour: 60 * 60 * 1000,
    minute: 60 * 1000,
}

export function isBrushableUnit(unit: string): boolean {
    return unit in BUCKET_LABEL_PATTERNS
}

function wallClockToMs(year: number, month: number, day: number, hour: number, minute: number, tz: string): number | null {
    const wallClock = new Date(Date.UTC(year, month - 1, day, hour, minute)).toISOString().slice(0, 19)
    const parsed = dayjs.tz(wallClock, tz)
    return parsed.isValid() && parsed.format("YYYY-MM-DDTHH:mm:ss") === wallClock ? parsed.valueOf() : null
}

export function bucketLabelToDateRange(label: string, unit: string, tz: string): DateRange | null {
    const match = BUCKET_LABEL_PATTERNS[unit]?.exec(label)
    if (!match) return null

    const [year, month, day, hour = 0, minute = 0] = match.slice(1).map(Number)
    const startMs = wallClockToMs(year, month, day, hour, minute, tz)
    if (startMs === null) return null

    const endMs = unit === "day"
        ? wallClockToMs(year, month, day + 1, 0, 0, tz)
        : startMs + FIXED_BUCKET_MS[unit]

    return endMs === null ? null : {startMs, endMs}
}

export function pixelSelectionToBucketIndices(
    xStart: number,
    xEnd: number,
    bucketPx: number[],
): {start: number; end: number} | null {
    if (bucketPx.length === 0) return null

    const findNearest = (px: number): number => {
        let best = 0
        let bestDist = Infinity
        for (let i = 0; i < bucketPx.length; i++) {
            const d = Math.abs(bucketPx[i] - px)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    const start = findNearest(Math.min(xStart, xEnd))
    const end = findNearest(Math.max(xStart, xEnd))

    return start <= end ? {start, end} : {start: end, end: start}
}

/** Drops every relative and absolute time filter, then sets `timeRange` as the relative one when given. */
export function withoutTimeFilters(routeQuery: LocationQueryRaw, timeRange: string | undefined): LocationQueryRaw {
    const query = normalizeRouteTimeRangeFilter(routeQuery, timeRange)
    for (const key of Object.keys(query)) {
        if (ABSOLUTE_DATE_KEY_PREFIXES.some((prefix) => key.startsWith(prefix))) delete query[key]
    }
    return query
}

export function buildBrushTimeRangeQuery(routeQuery: LocationQueryRaw, range: BrushRange, pageKey: string): LocationQueryRaw {
    return {
        ...withoutTimeFilters(routeQuery, undefined),
        [BRUSH_START_KEY]: range.startDate,
        [BRUSH_END_KEY]: range.endDate,
        [pageKey]: "1",
    }
}

export function buildBrushClearQuery(routeQuery: LocationQueryRaw, pageKey: string): LocationQueryRaw {
    const defaultTimeRange = applyDefaultFilters({}, {namespace: null, includeTimeRange: true}).query[TIME_RANGE_EQUALS_KEY]

    return {
        ...withoutTimeFilters(routeQuery, typeof defaultTimeRange === "string" ? defaultTimeRange : undefined),
        [pageKey]: "1",
    }
}

export function readBrushRange(routeQuery: LocationQuery | LocationQueryRaw): BrushRange | undefined {
    const startDate = routeQuery[BRUSH_START_KEY]
    const endDate = routeQuery[BRUSH_END_KEY]

    return typeof startDate === "string" && typeof endDate === "string" ? {startDate, endDate} : undefined
}
