/**
 * RFC 9457 Problem Details — the single error representation of the Kestra API.
 *
 * Branch on `type`: it is a stable, documented URI, unlike `title` (display text) or `detail`
 * (per-occurrence text). Never match on message prose — that coupling is what this format replaces.
 */

/** Base of every problem `type` URI. Each slug appended to it resolves to that type's documentation. */
export const PROBLEM_TYPE_BASE = "https://kestra.io/docs/api-reference/problems/"

/**
 * A problem details document.
 *
 * Deliberately closed — no index signature — so a narrowed value cannot be used to read a field outside the
 * format. That is not a complete guard, since a `catch (e: any)` call site bypasses it, which is why
 * `noLegacyErrorFields.spec.ts` also scans for reads of fields this format does not carry. Reach vendor
 * extensions through {@link KestraProblemError.problem} rather than widening this type.
 */
export interface ProblemDetail {
    /** Stable identifier of the problem kind. The only member to branch on. */
    readonly type: string
    /** Short, human-readable summary. Stable for a given `type` and never parameterised. */
    readonly title: string
    /** The HTTP status code. */
    readonly status: number
    /** Human-readable explanation specific to this occurrence. Not localizable — display as-is. */
    readonly detail?: string
    /** The path of the request that failed. */
    readonly instance?: string
    /** Field-level errors, when several problems are reported at once. */
    readonly errors?: readonly ProblemFieldError[]
    /** Correlation id for the server log entry. Present on server errors only. */
    readonly traceId?: string
}

/** One field-level error inside a {@link ProblemDetail}. */
export interface ProblemFieldError {
    /** What is wrong with this field. */
    readonly detail: string
    /** RFC 6901 JSON Pointer into the submitted document, e.g. `/tasks/0/type`. Machine locator. */
    readonly pointer?: string
    /** Friendlier locator naming tasks and inputs by id, e.g. `tasks[my-task].type`. Not a JSON Pointer. */
    readonly path?: string
    /** Problem type of this individual error, when it varies per item. Same URI scheme as the document. */
    readonly type?: string
}

/**
 * The error thrown for any `application/problem+json` response.
 *
 * `message` is `detail ?? title` — the most useful human string available, and deliberately not prefixed
 * with the status, so a call site that surfaces `err.message` shows something a user can act on.
 */
export class KestraProblemError extends Error implements ProblemDetail {
    readonly type: string
    readonly title: string
    readonly status: number
    readonly detail?: string
    readonly instance?: string
    readonly errors?: readonly ProblemFieldError[]
    readonly traceId?: string
    /** The raw body, including any extension member this interface does not declare. */
    readonly problem: ProblemDetail & Record<string, unknown>

    constructor(body: ProblemDetail, httpStatus?: number) {
        super(body.detail ?? body.title)
        this.name = "KestraProblemError"
        this.type = body.type
        this.title = body.title
        this.status = body.status ?? httpStatus ?? 0
        this.detail = body.detail
        this.instance = body.instance
        this.errors = body.errors
        this.traceId = body.traceId
        // The parsed body carries whatever extension members the server sent; only the declared ones are
        // typed, and `problem` is how a caller reaches the rest.
        this.problem = body as ProblemDetail & Record<string, unknown>
    }
}

/** Whether a parsed body is a problem document. Used when the content type is unavailable or rewritten. */
export function isProblemDetail(body: unknown): body is ProblemDetail {
    if (body === null || typeof body !== "object") return false
    const candidate = body as Record<string, unknown>
    return typeof candidate.type === "string" && typeof candidate.title === "string"
}

/**
 * Parses a raw response body, for call sites that use `fetch` directly and so never reach the SDK
 * interceptor — the login endpoint, for one. Returns undefined when the body is not a problem document.
 */
export function parseProblem(
    bodyText: string,
    status: number,
    contentType?: string | null,
): ProblemDetail | undefined {
    const looksLikeProblem = contentType?.includes("application/problem+json") ?? true
    if (!looksLikeProblem && !contentType?.includes("json")) return undefined

    try {
        const parsed: unknown = JSON.parse(bodyText)
        if (!isProblemDetail(parsed)) return undefined
        return parsed.status ? parsed : {...parsed, status}
    } catch {
        return undefined
    }
}

/** The problem document behind a thrown value, wherever it ended up. Undefined if there is none. */
export function asProblem(error: unknown): ProblemDetail | undefined {
    if (error instanceof KestraProblemError) return error.problem
    if (error === null || typeof error !== "object") return undefined

    const candidate = error as Record<string, unknown>
    if (isProblemDetail(candidate.problem)) return candidate.problem
    // A body read straight off an axios-like response, e.g. via the useClient facade.
    const data = (candidate.response as Record<string, unknown> | undefined)?.data
    if (isProblemDetail(data)) return data
    if (isProblemDetail(candidate)) return candidate
    return undefined
}

/**
 * The slug identifying a problem type, e.g. `entity-already-exists`.
 *
 * Comparisons use the slug rather than the full URI so a change of docs domain or base path cannot
 * silently stop a branch from matching. Slugs are unique by construction: the URI *is* base + slug.
 */
export function problemSlug(type: string | undefined): string | undefined {
    if (!type || type === "about:blank") return undefined
    if (type.startsWith(PROBLEM_TYPE_BASE)) return type.slice(PROBLEM_TYPE_BASE.length)
    return type.split("/").pop() || undefined
}

/** Whether a thrown value is a problem of any of the given types. */
export function isProblemType(error: unknown, ...types: readonly string[]): boolean {
    const slug = problemSlug(asProblem(error)?.type)
    return slug !== undefined && types.includes(slug)
}
