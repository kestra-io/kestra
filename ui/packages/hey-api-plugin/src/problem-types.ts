/**
 * The problem types a client may need to name.
 *
 * Not every constant is branched on today: the toast renders `title` and `detail` generically, so a type
 * only needs a branch when it drives behaviour — a create-then-update fallback, a redirect, a dialog.
 *
 * These mirror the backend catalog (`io.kestra.webserver.errors.ProblemTypes` and its Enterprise
 * counterpart). Keep them in sync when adding a branch: a slug absent server-side silently stops matching.
 */
export const ProblemTypes = {
    /** Creating an entity whose id is already taken. Drives create-then-update fallbacks. */
    ENTITY_ALREADY_EXISTS: "entity-already-exists",
    ENTITY_NOT_FOUND: "not-found",
    VALIDATION_FAILED: "validation-failed",
    BULK_VALIDATION_FAILED: "bulk-validation-failed",
    FORBIDDEN: "forbidden",
    UNAUTHENTICATED: "unauthenticated",
    CONFLICT: "conflict",
    TOO_MANY_REQUESTS: "too-many-requests",
    SERVICE_UNAVAILABLE: "service-unavailable",
    INTERNAL_ERROR: "internal-error",
} as const

export type ProblemType = (typeof ProblemTypes)[keyof typeof ProblemTypes]
