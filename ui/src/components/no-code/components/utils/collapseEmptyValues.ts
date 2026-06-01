export function collapseEmptyValues(value: unknown): unknown {
    return value === "" || value === null || JSON.stringify(value) === "{}" ? undefined : value
}
