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
