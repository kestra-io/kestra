export type PathSegments = string | readonly (string | number)[];

export interface Scheduled<A extends unknown[]> {
    (...args: A): void;
    cancel(): void;
    flush(): void;
}

const HTML_ESCAPES: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;",
}

const UNESCAPED_HTML = /[&<>"']/g

const BRACKET_ACCESSOR = /\[["']?([^"'\]]+)["']?]/g

/**
 * Escapes `&`, `<`, `>`, `"` and `'` so the value can be interpolated into an
 * `v-html` template. Nullish values become an empty string.
 */
export function escapeHtml(value: unknown): string {
    if (value === null || value === undefined) {
        return ""
    }
    return String(value).replace(UNESCAPED_HTML, (character) => HTML_ESCAPES[character])
}

export function isPlainObject(value: unknown): value is Record<string, unknown> {
    if (value === null || typeof value !== "object") {
        return false
    }
    const prototype = Object.getPrototypeOf(value)
    return prototype === null || prototype === Object.prototype
}

/**
 * Deep-clones arrays, dates, maps, sets and plain objects. Anything else
 * (files, class instances, functions) is kept by reference.
 */
export function cloneDeep<T>(value: T): T {
    if (Array.isArray(value)) {
        return value.map((item) => cloneDeep(item)) as T
    }
    if (value instanceof Date) {
        return new Date(value.getTime()) as T
    }
    if (value instanceof Map) {
        return new Map([...value].map(([key, item]) => [cloneDeep(key), cloneDeep(item)])) as T
    }
    if (value instanceof Set) {
        return new Set([...value].map((item) => cloneDeep(item))) as T
    }
    if (!isPlainObject(value)) {
        return value
    }
    const result: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value)) {
        result[key] = cloneDeep(item)
    }
    return result as T
}

/**
 * Recursively merges `source` into a copy of `target`; neither argument is mutated.
 * `undefined` source values are skipped and source arrays replace target arrays.
 */
export function deepMerge<T extends object, S extends object>(target: T | null | undefined, source: S): T & S {
    const result: Record<string, unknown> = isPlainObject(target) ? {...target} : {}
    for (const [key, value] of Object.entries(source ?? {})) {
        if (value === undefined) {
            continue
        }
        result[key] = isPlainObject(value) ? deepMerge(result[key] as object, value) : value
    }
    return result as T & S
}

export function isDeepEqual(a: unknown, b: unknown): boolean {
    if (a === b) {
        return true
    }
    if (typeof a === "number" && typeof b === "number") {
        return Number.isNaN(a) && Number.isNaN(b)
    }
    if (a instanceof Date && b instanceof Date) {
        return a.getTime() === b.getTime()
    }
    if (Array.isArray(a) || Array.isArray(b)) {
        return Array.isArray(a) && Array.isArray(b)
            && a.length === b.length
            && a.every((item, index) => isDeepEqual(item, b[index]))
    }
    if (!isPlainObject(a) || !isPlainObject(b)) {
        return false
    }
    const keys = Object.keys(a)
    return keys.length === Object.keys(b).length
        && keys.every((key) => Object.prototype.hasOwnProperty.call(b, key) && isDeepEqual(a[key], b[key]))
}

/** Runs `callback` once `wait` ms have passed without a new call. */
export function debounce<A extends unknown[]>(callback: (...args: A) => unknown, wait: number): Scheduled<A> {
    let timer: ReturnType<typeof setTimeout> | undefined
    let pending: A | undefined

    const invoke = () => {
        timer = undefined
        const args = pending
        pending = undefined
        if (args) {
            callback(...args)
        }
    }

    const debounced = (...args: A) => {
        pending = args
        clearTimeout(timer)
        timer = setTimeout(invoke, wait)
    }

    debounced.cancel = () => {
        clearTimeout(timer)
        timer = undefined
        pending = undefined
    }

    debounced.flush = () => {
        if (timer !== undefined) {
            clearTimeout(timer)
            invoke()
        }
    }

    return debounced
}

/** Runs `callback` on the leading edge, then at most once per `wait` ms; `leading: false` skips the first edge. */
export function throttle<A extends unknown[]>(
    callback: (...args: A) => unknown,
    wait: number,
    options: {leading?: boolean} = {},
): Scheduled<A> {
    let timer: ReturnType<typeof setTimeout> | undefined
    let pending: A | undefined
    let lastCall = 0

    const invoke = (args: A) => {
        lastCall = Date.now()
        pending = undefined
        callback(...args)
    }

    const throttled = (...args: A) => {
        if (options.leading === false && lastCall === 0) {
            lastCall = Date.now()
        }
        const remaining = wait - (Date.now() - lastCall)
        if (remaining <= 0) {
            clearTimeout(timer)
            timer = undefined
            invoke(args)
            return
        }
        pending = args
        timer ??= setTimeout(() => {
            timer = undefined
            if (pending) {
                invoke(pending)
            }
        }, remaining)
    }

    throttled.cancel = () => {
        clearTimeout(timer)
        timer = undefined
        pending = undefined
    }

    throttled.flush = () => {
        if (pending) {
            clearTimeout(timer)
            timer = undefined
            invoke(pending)
        }
    }

    return throttled
}

function toPathSegments(path: PathSegments): string[] {
    if (Array.isArray(path)) {
        return path.map(String)
    }
    return String(path)
        .replace(BRACKET_ACCESSOR, ".$1")
        .split(".")
        .filter((segment) => segment !== "")
}

/** Reads a dotted or bracketed path (`a.b[0].c`), falling back to `defaultValue`. */
export function getPath<T = unknown>(object: unknown, path: PathSegments, defaultValue?: T): T | undefined {
    let current = object
    for (const segment of toPathSegments(path)) {
        if (current === null || current === undefined) {
            return defaultValue
        }
        current = (current as Record<string, unknown>)[segment]
    }
    return current === undefined ? defaultValue : current as T
}

/** Writes at a dotted or bracketed path, creating missing objects and arrays on the way. */
export function setPath(object: Record<string, unknown>, path: PathSegments, value: unknown): void {
    const segments = toPathSegments(path)
    if (segments.length === 0) {
        return
    }
    let current = object
    for (let index = 0; index < segments.length - 1; index++) {
        const segment = segments[index]
        const next = current[segment]
        if (next === null || typeof next !== "object") {
            current[segment] = /^\d+$/.test(segments[index + 1]) ? [] : {}
        }
        current = current[segment] as Record<string, unknown>
    }
    current[segments[segments.length - 1]] = value
}

export function groupBy<T>(items: Iterable<T>, iteratee: (item: T) => string | number): Record<string, T[]> {
    const result: Record<string, T[]> = {}
    for (const item of items) {
        const key = String(iteratee(item))
        result[key] ??= []
        result[key].push(item)
    }
    return result
}

export function mapValues<T, R>(object: Record<string, T>, iteratee: (value: T, key: string) => R): Record<string, R> {
    return Object.fromEntries(
        Object.entries(object).map(([key, value]) => [key, iteratee(value, key)]),
    ) as Record<string, R>
}
