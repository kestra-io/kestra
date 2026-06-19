import {dayjs} from "@kestra-io/design-system"

export type DateRange = {startMs: number; endMs: number}

const NON_INVERTIBLE_FORMATS = new Set(["yyyy-MM", "yyyy-'W'ww"])

const BUCKET_LABEL_PARSERS: Record<string, {pattern: RegExp; durationMs: number}> = {
    "yyyy-MM-DD": {pattern: /^(\d{4})-(\d{2})-(\d{2})$/, durationMs: 24 * 60 * 60 * 1000},
    "yyyy-MM-DD:HH:00": {pattern: /^(\d{4})-(\d{2})-(\d{2}):(\d{2}):00$/, durationMs: 60 * 60 * 1000},
    "yyyy-MM-DD:HH:mm": {pattern: /^(\d{4})-(\d{2})-(\d{2}):(\d{2}):(\d{2})$/, durationMs: 60 * 1000},
}

export function bucketLabelToDateRange(label: string, format: string, tz: string): DateRange | null {
    if (NON_INVERTIBLE_FORMATS.has(format)) return null

    const parser = BUCKET_LABEL_PARSERS[format]
    const match = parser?.pattern.exec(label)
    if (!parser || !match) return null

    const [, year, month, day, hour = "00", minute = "00"] = match
    const localDateTime = `${year}-${month}-${day}T${hour}:${minute}:00`
    const parsed = dayjs.tz(localDateTime, tz)

    if (!parsed.isValid() || parsed.format("YYYY-MM-DDTHH:mm:ss") !== localDateTime) return null

    const startMs = parsed.valueOf()
    return {startMs, endMs: startMs + parser.durationMs}
}

export function pixelSelectionToBucketIndices(
    xStart: number,
    xEnd: number,
    bucketPx: number[],
): {start: number; end: number} | null {
    if (bucketPx.length === 0) return null

    const lo = Math.min(xStart, xEnd)
    const hi = Math.max(xStart, xEnd)

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

    let startIdx = findNearest(lo)
    let endIdx = findNearest(hi)

    startIdx = Math.max(0, Math.min(startIdx, bucketPx.length - 1))
    endIdx = Math.max(0, Math.min(endIdx, bucketPx.length - 1))

    if (startIdx > endIdx) [startIdx, endIdx] = [endIdx, startIdx]

    return {start: startIdx, end: endIdx}
}

export function buildBrushTimeRangeQuery(
    routeQuery: Record<string, string | string[] | undefined>,
    isoStart: string,
    isoEnd: string,
    pageKey: string,
): Record<string, string | undefined> {
    const result: Record<string, string | undefined> = {...routeQuery} as Record<string, string | undefined>

    delete result.timeRange
    for (const key of Object.keys(result)) {
        if (key.startsWith("filters[timeRange]")) {
            delete result[key]
        }
    }

    result.startDate = isoStart
    result.endDate = isoEnd
    result[pageKey] = "1"

    return result
}
