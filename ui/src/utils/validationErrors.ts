import type {ValidationError} from "@kestra-io/kestra-sdk"

export type {ValidationError}

/** One readable line per error, e.g. `tasks[log].message: must not be null`. */
export function validationErrorLines(errors?: ValidationError[]): string[] {
    return (errors ?? []).flatMap(({detail, path}) => detail ? [path ? `${path}: ${detail}` : detail] : [])
}

export function pointerSegments(pointer: string): string[] {
    return pointer.split("/").slice(1).map(segment => segment.replace(/~1/g, "/").replace(/~0/g, "~"))
}

/** A pointer names Java properties, so `_finally` addresses the YAML key `finally`. */
export function pointerKeys(segment: string): string[] {
    return segment.startsWith("_") ? [segment, segment.slice(1)] : [segment]
}

export function fieldPathOf(segments: string[]): string {
    return segments.reduce((path, segment) => /^\d+$/.test(segment)
        ? `${path}[${segment}]`
        : `${path}${path ? "." : ""}${pointerKeys(segment).at(-1)}`, "")
}

function matchesPrefix(segments: string[], prefix: (string | number)[]): boolean {
    return segments.length > prefix.length
        && prefix.every((expected, index) => pointerKeys(segments[index]).includes(String(expected)))
}

/** Keyed by field path relative to `prefix`, the pointer of the block the form is rendering. */
export function errorsByFieldPath(
    errors: ValidationError[] | undefined,
    prefix: (string | number)[] = [],
): Map<string, string[]> {
    const grouped = new Map<string, string[]>()
    for (const {pointer, detail} of errors ?? []) {
        if (!pointer || !detail) continue
        const segments = pointerSegments(pointer)
        if (!matchesPrefix(segments, prefix)) continue
        const path = fieldPathOf(segments.slice(prefix.length))
        grouped.set(path, [...(grouped.get(path) ?? []), detail])
    }
    return grouped
}

export function hasErrorUnder(errors: Map<string, string[]>, path: string): boolean {
    for (const key of errors.keys()) {
        if (key === path || key.startsWith(`${path}.`) || key.startsWith(`${path}[`)) return true
    }
    return false
}
