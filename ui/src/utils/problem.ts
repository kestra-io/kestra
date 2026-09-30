/**
 * Presentation of RFC 9457 problem details.
 *
 * The transport-level helpers (`asProblem`, `isProblemType`, …) live in the SDK; this module holds the
 * part that needs `t`, i.e. turning a problem document into text for a user.
 *
 * `title` is stable per `type` and never parameterised, which makes it usable as a translation key —
 * so error titles are localized for the first time. `detail` is specific to one occurrence and carries
 * interpolated values, so it can only be shown as the backend wrote it.
 */
import {asProblem, problemSlug, type ProblemDetail, type ProblemFieldError} from "@kestra-io/kestra-sdk"

/** Minimal shape of the vue-i18n helpers this module needs, so it stays testable without a full app. */
type Translate = (key: string, named?: Record<string, unknown>) => string
type TranslateExists = (key: string) => boolean

/**
 * A message for the global error toast.
 *
 * Previously declared inside `ErrorToast.vue` and imported by the store — a layering inversion. Pass a
 * `problem` and let the toast derive its own text; `title`/`content` are for the rare caller that has
 * already localized something itself.
 */
export interface ToastMessage {
    variant?: "success" | "warning" | "info" | "error" | "primary"
    /** Pre-localized title. Wins over anything derived from `problem`. */
    title?: string
    /** Pre-localized body, rendered as markdown. Wins over `problem.detail`. */
    content?: string
    problem?: ProblemDetail
    status?: number
    request?: {method: string; url: string}
}

const KEY_PREFIX = "errors.problems."

/**
 * The toast title: the localized name of this kind of problem.
 *
 * Falls back to the `title` the server sent when no key exists, so a problem type added server-side
 * renders correctly straight away instead of waiting for the UI to ship a translation.
 */
export function problemTitle(problem: ProblemDetail | undefined, t: Translate, te: TranslateExists): string {
    const key = keyFor(problem, "title")
    if (key && te(key)) return t(key)
    return problem?.title || t("errors.generic.title")
}

/**
 * The toast body. Shown as the server wrote it, because it is specific to this occurrence.
 *
 * The one exception is a problem whose detail never varies — a service being unavailable, say — which may
 * declare a `detail` key and be localized like the title.
 */
export function problemDetail(problem: ProblemDetail | undefined, t: Translate, te: TranslateExists): string {
    const key = keyFor(problem, "detail")
    if (key && te(key)) return t(key)
    return problem?.detail ?? ""
}

/**
 * Which field an error refers to. Prefers `path` (`tasks[my-task].type`) over `pointer` (`/tasks/0/type`):
 * the former is what the user sees in their own YAML, the latter is index-based and means nothing to them.
 */
export function problemFieldLabel(item: ProblemFieldError): string | undefined {
    return item.path || item.pointer || undefined
}

/**
 * The text for one field-level error. An item carrying its own `type` can be localized, which is how bulk
 * operations get per-item translations without the server sending i18n keys.
 */
export function problemFieldMessage(item: ProblemFieldError, t: Translate, te: TranslateExists): string {
    const slug = problemSlug(item.type)
    const key = slug ? `${KEY_PREFIX}${slug}.detail` : undefined
    if (key && te(key)) return t(key, {value: problemFieldLabel(item) ?? ""})
    return item.detail
}

/**
 * The body of a toast reporting a bulk operation: one entry per rejected item.
 *
 * Falls back to the problem's own detail, because a network failure reaches these call sites too and
 * carries no `errors[]` — an empty list would render as a toast with a blank body.
 */
export function problemBulkBody(
    problem: ProblemDetail | undefined,
    t: Translate,
    te: TranslateExists,
): string | {message: string}[] {
    const errors = problem?.errors ?? []
    if (errors.length > 0) {
        return errors.map((item) => ({message: problemFieldMessage(item, t, te)}))
    }
    return problemDetail(problem, t, te) || t("errors.generic.content")
}

/** Builds a toast message from whatever a call site caught. */
export function toToastMessage(error: unknown, variant: ToastMessage["variant"] = "error"): ToastMessage {
    const problem = asProblem(error)
    return {variant, problem, status: problem?.status}
}

function keyFor(problem: ProblemDetail | undefined, member: "title" | "detail"): string | undefined {
    const slug = problemSlug(problem?.type)
    return slug ? `${KEY_PREFIX}${slug}.${member}` : undefined
}
