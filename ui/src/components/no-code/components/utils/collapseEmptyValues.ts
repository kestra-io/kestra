export function collapseEmptyValues<T>(value: T): T | undefined {
    return value === "" || value === null || JSON.stringify(value) === "{}" ? undefined : value
}
