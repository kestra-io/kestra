function isNullOrUndefined(value: unknown): value is null | undefined {
    return value === null || value === undefined
}

export function removeNullAndUndefined<T>(obj: T): T | undefined {
    if (Array.isArray(obj)) {
        const ar = obj
            .map(item => removeNullAndUndefined(item))
            .filter((item): item is NonNullable<typeof item> => isNullOrUndefined(item) === false)

        return ar.length > 0 ? (ar as T) : undefined
    }

    if (obj !== null && typeof obj === "object") {
        const newObj: Record<string, unknown> = {}
        let hasValue = false

        for (const [key, rawValue] of Object.entries(obj as Record<string, unknown>)) {
            if (isNullOrUndefined(rawValue)) {
                continue
            }

            const newVal = removeNullAndUndefined(rawValue)
            if (isNullOrUndefined(newVal)) {
                continue
            }

            hasValue = true
            newObj[key] = newVal
        }

        return hasValue ? (newObj as T) : undefined
    }

    return obj
}
