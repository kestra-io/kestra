const TIME_RANGE_FILTER_PREFIX = "filters[timeRange]["
const TIME_RANGE_EQUALS_FILTER_KEY = "filters[timeRange][EQUALS]"
const LEGACY_TIME_RANGE_FILTER_KEY = "timeRange"

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
