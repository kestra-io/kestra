/** An entry of the RFC 9457 `errors` member, as a validation result carries it. */
export interface ValidationError {
    detail?: string;
    pointer?: string;
    path?: string;
}

/** One readable line per error, e.g. `tasks[log].message: must not be null`. */
export function validationErrorLines(errors?: ValidationError[]): string[] {
    return (errors ?? []).flatMap(({detail, path}) => detail ? [path ? `${path}: ${detail}` : detail] : [])
}
