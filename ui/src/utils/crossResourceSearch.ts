import _escape from "lodash/escape"

export type SearchResourceType = "flows" | "files" | "kv" | "secrets"

export const SEARCH_RESOURCE_TYPES: SearchResourceType[] = ["flows", "files", "kv", "secrets"]

export type SearchStatus = "idle" | "counting" | "done" | "failed"

export type CrossSearchSelection =
    | {type: "flows"; namespace: string; id: string; line: number; column: number}
    | {type: "files"; namespace: string; path: string}
    | {type: "kv"; namespace: string; key: string}
    | {type: "secrets"; namespace: string; key: string}

/**
 * Mirrors `SourceSearchMatcher.toPattern`'s literal, case-insensitive-by-default semantics
 * (core/src/main/java/io/kestra/core/utils/SourceSearchMatcher.java) so the three types that only
 * ever match plain text behave consistently with Flows when case sensitivity isn't requested.
 */
export function matchesLiteral(haystack: string, query: string, caseSensitive = false): boolean {
    if (!query) return false
    return caseSensitive ? haystack.includes(query) : haystack.toLowerCase().includes(query.toLowerCase())
}

export interface HighlightSegment {
    text: string;
    matched: boolean;
}

export function buildHighlightSegments(text: string, query: string, caseSensitive = false): HighlightSegment[] {
    if (!query) return [{text, matched: false}]

    const haystack = caseSensitive ? text : text.toLowerCase()
    const needle = caseSensitive ? query : query.toLowerCase()

    let cursor = 0
    let index = haystack.indexOf(needle, cursor)
    if (index === -1) return [{text, matched: false}]

    const segments: HighlightSegment[] = []
    while (index !== -1) {
        if (index > cursor) segments.push({text: text.slice(cursor, index), matched: false})
        segments.push({text: text.slice(index, index + query.length), matched: true})
        cursor = index + query.length
        index = haystack.indexOf(needle, cursor)
    }
    if (cursor < text.length) segments.push({text: text.slice(cursor), matched: false})

    return segments
}

/**
 * Renders `buildHighlightSegments` as escaped HTML with matches wrapped in `<mark>` — the shared
 * rendering used by both the results list and the preview pane for files/KV/secrets rows, which
 * (unlike flows) have no server-provided `[mark]` markers to highlight against.
 */
export function buildHighlightHtml(text: string, query: string, caseSensitive = false): string {
    return buildHighlightSegments(text, query, caseSensitive)
        .map((segment) => segment.matched ? `<mark>${_escape(segment.text)}</mark>` : _escape(segment.text))
        .join("")
}

/**
 * The KV and namespace-file endpoints split the query into terms and require every term to occur as
 * a substring, in any order — so a query like `landing.bucket` matches `landing-bucket-us-east-1`
 * even though that literal string never appears. Highlighting the whole query would then mark
 * nothing and leave the row looking like an unexplained result.
 */
export function splitQueryTerms(query: string): string[] {
    return query.split(/[^\p{L}\p{N}]+/u).filter(Boolean)
}

export function buildTermHighlightSegments(text: string, query: string, caseSensitive = false): HighlightSegment[] {
    // When the query does occur verbatim, mark it as one span — splitting it would fragment a match
    // the user can already read. Per-term marking is the fallback for the rows only the term model found.
    if (matchesLiteral(text, query, caseSensitive)) return buildHighlightSegments(text, query, caseSensitive)

    const terms = splitQueryTerms(query)
    if (terms.length === 0) return [{text, matched: false}]

    const haystack = caseSensitive ? text : text.toLowerCase()
    const matched = new Array<boolean>(text.length).fill(false)

    for (const term of terms) {
        const needle = caseSensitive ? term : term.toLowerCase()
        let index = haystack.indexOf(needle)
        while (index !== -1) {
            for (let i = index; i < index + needle.length; i++) matched[i] = true
            index = haystack.indexOf(needle, index + 1)
        }
    }

    const segments: HighlightSegment[] = []
    let start = 0
    for (let i = 1; i <= text.length; i++) {
        if (i === text.length || matched[i] !== matched[start]) {
            segments.push({text: text.slice(start, i), matched: matched[start]})
            start = i
        }
    }

    return segments.length > 0 ? segments : [{text, matched: false}]
}

export function buildTermHighlightHtml(text: string, query: string, caseSensitive = false): string {
    return buildTermHighlightSegments(text, query, caseSensitive)
        .map((segment) => segment.matched ? `<mark>${_escape(segment.text)}</mark>` : _escape(segment.text))
        .join("")
}

export interface PathSegment extends HighlightSegment {
    dim: boolean;
}

/**
 * Splits a namespace-file path into directory segments (dimmed, since they carry no information
 * about the match) and a filename segment, highlighting the query within each independently.
 */
export function buildPathSegments(path: string, query: string, caseSensitive = false): PathSegment[] {
    const lastSlash = path.lastIndexOf("/")
    if (lastSlash === -1) {
        return buildTermHighlightSegments(path, query, caseSensitive).map((segment) => ({...segment, dim: false}))
    }

    const directory = path.slice(0, lastSlash + 1)
    const fileName = path.slice(lastSlash + 1)

    return [
        ...buildTermHighlightSegments(directory, query, caseSensitive).map((segment) => ({...segment, dim: true})),
        ...buildTermHighlightSegments(fileName, query, caseSensitive).map((segment) => ({...segment, dim: false})),
    ]
}

export type SearchViewState = "initial" | "loading" | "empty" | "results"

export interface SearchViewStateInput {
    /** The user has typed something. */
    hasQuery: boolean;
    /** `useRestoreUrl`'s gate: false while a restore navigation is still in flight. */
    loadInit: boolean;
    /** A search is scheduled (debounce) or in flight, but no type has flipped to counting yet. */
    searchPending: boolean;
    /** At least one selected type is counting. */
    anyCounting: boolean;
    /** Matches currently known across the selected types. */
    matchCount: number;
}

/**
 * Single source of truth for which of the four page states to render.
 *
 * `searchPending` is what keeps "empty" honest: the store only knows about a search once a request
 * is out, so between the keystroke and that request (the debounce) every status is still idle and
 * every count still zero — indistinguishable from a finished search that found nothing.
 */
export function searchViewState(input: SearchViewStateInput): SearchViewState {
    if (!input.hasQuery) return "initial"
    const searching = input.searchPending || input.anyCounting
    if (!input.loadInit || (searching && input.matchCount === 0)) return "loading"
    if (!searching && input.matchCount === 0) return "empty"
    return "results"
}

export function crossSearchResultKey(selection: CrossSearchSelection): string {
    switch (selection.type) {
        case "flows":
            return `flows:${selection.namespace}.${selection.id}#${selection.line}:${selection.column}`
        case "files":
            return `files:${selection.namespace}#${selection.path}`
        case "kv":
            return `kv:${selection.namespace}#${selection.key}`
        case "secrets":
            return `secrets:${selection.namespace}#${selection.key}`
    }
}

/**
 * Groups a flat list of namespaced entries (as returned by the KV/Secrets list endpoints) into
 * per-namespace buckets, preserving first-seen namespace order.
 */
export function groupByNamespace<T, M>(entries: T[], namespaceOf: (entry: T) => string, mapper: (entry: T) => M): {namespace: string; matches: M[]}[] {
    const order: string[] = []
    const groups = new Map<string, M[]>()

    for (const entry of entries) {
        const namespace = namespaceOf(entry)
        if (!groups.has(namespace)) {
            groups.set(namespace, [])
            order.push(namespace)
        }
        groups.get(namespace)!.push(mapper(entry))
    }

    return order.map((namespace) => ({namespace, matches: groups.get(namespace)!}))
}
