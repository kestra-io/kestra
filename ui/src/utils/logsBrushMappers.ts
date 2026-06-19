import moment from "moment-timezone"

export type DateRange = {startMs: number; endMs: number}

const NON_INVERTIBLE_FORMATS = new Set(["yyyy-MM", "yyyy-'W'ww"])

export function bucketLabelToDateRange(label: string, format: string, tz: string): DateRange | null {
    if (NON_INVERTIBLE_FORMATS.has(format)) return null

    let parsed: moment.Moment
    let bucketDurationMs: number

    if (format === "yyyy-MM-DD") {
        parsed = moment.tz(label, "YYYY-MM-DD", true, tz)
        bucketDurationMs = 24 * 60 * 60 * 1000
    } else if (format === "yyyy-MM-DD:HH:00") {
        parsed = moment.tz(label, "YYYY-MM-DD[:]HH[:]00", true, tz)
        bucketDurationMs = 60 * 60 * 1000
    } else if (format === "yyyy-MM-DD:HH:mm") {
        parsed = moment.tz(label, "YYYY-MM-DD[:]HH[:]mm", true, tz)
        bucketDurationMs = 60 * 1000
    } else {
        return null
    }

    if (!parsed.isValid()) return null

    const startMs = parsed.valueOf()
    return {startMs, endMs: startMs + bucketDurationMs}
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
