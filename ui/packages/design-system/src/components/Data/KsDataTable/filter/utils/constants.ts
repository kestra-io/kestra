/**
 * Shared constants for filter URL handling. Extracted from inline literals scattered across the
 * filter codebase to keep the magic strings in one place.
 */

/** Route query key for the freeform search input (the `q` chip). */
export const SEARCH_QUERY_KEY = "filters[q][EQUALS]"

/** Filter-key shorthand for the timeRange chip (split into startDate/endDate on the wire). */
export const TIME_RANGE_KEY = "timeRange"

/** Route query key that carries the dateFilter meta selector for timeRange. */
export const DATE_FILTER_KEY = "dateFilter"

/**
 * Maximum number of `[and|or][N]` prefix segments the chip UI can render.
 * The chip UI supports a top-level group plus one wrapper inside it (2 segments).
 * A wrapper containing another wrapper (3+ segments) falls back to the raw editor.
 */
export const MAX_RENDERABLE_NESTING_DEPTH = 2

/** Date-field keys that are encoded separately on the wire from the timeRange chip. */
export const START_DATE_FIELD = "startDate"
export const END_DATE_FIELD = "endDate"
