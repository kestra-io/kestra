const TIME_RANGE_FILTER_PREFIX = "filters[timeRange]["
const TIME_RANGE_EQUALS_FILTER_KEY = "filters[timeRange][EQUALS]"
const LEGACY_TIME_RANGE_FILTER_KEY = "timeRange"

/**
 * Normalize a route query to express the time-range filter as a single
 * `filters[timeRange][EQUALS]=<value>` entry.
 *
 * Mirrors `normalizeRouteLevelFilter`:
 *  - strips any other `filters[timeRange][*]` comparator,
 *  - strips the legacy top-level `timeRange` key,
 *  - strips explicit `startDate`/`endDate` (the time-range value re-derives them),
 *  - drops the filter entirely when `value` is undefined.
 */
export const normalizeRouteTimeRangeFilter = (
    query: Record<string, any>,
    value: string | undefined,
) => {
    const normalized = {...query}

    Object.keys(normalized).forEach((key) => {
        if (key.startsWith(TIME_RANGE_FILTER_PREFIX)) {
            delete normalized[key]
        }
    })

    delete normalized[LEGACY_TIME_RANGE_FILTER_KEY]
    delete normalized.startDate
    delete normalized.endDate

    if (value) {
        normalized[TIME_RANGE_EQUALS_FILTER_KEY] = value
    }

    return normalized
}
