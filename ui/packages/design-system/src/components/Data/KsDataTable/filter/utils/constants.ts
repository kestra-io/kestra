/**
 * Shared constants for filter URL handling. Extracted from inline literals scattered across the
 * filter codebase to keep the magic strings in one place.
 */

/** Route query key for the freeform search input (the `q` chip). */
export const SEARCH_QUERY_KEY = "filters[q][EQUALS]"

/**
 * Maximum number of `[and|or][N]` prefix segments the chip UI can render.
 * The chip UI supports a top-level group plus one wrapper inside it (2 segments).
 * A wrapper containing another wrapper (3+ segments) falls back to the raw editor.
 */
export const MAX_RENDERABLE_NESTING_DEPTH = 2

/**
 * True when a value is a relative ISO-8601 duration (e.g. `PT24H`, `P7D`) rather than an
 * absolute instant. Durations start with an optional sign then `P` followed by `T` or a digit;
 * an absolute ISO date-time starts with a year digit, so the two never collide. Date-field chips
 * ({@code valueType: "time-range"}) carry such a duration for a relative window, resolved server-side.
 */
export const isRelativeDuration = (value: unknown): value is string =>
    typeof value === "string" && /^-?P(?=[T\d])/i.test(value)
