export function cloneDeep<T>(value: T): T {
    if (value === null || typeof value !== "object") {
        return value
    }
    if (value instanceof Date) {
        return new Date(value.getTime()) as unknown as T
    }
    if (Array.isArray(value)) {
        return value.map(cloneDeep) as unknown as T
    }
    const result = Object.create(Object.getPrototypeOf(value))
    for (const key of Object.keys(value as object)) {
        (result as any)[key] = cloneDeep((value as any)[key])
    }
    return result
}
