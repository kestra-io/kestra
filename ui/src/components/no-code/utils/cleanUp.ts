function isNullOrUndefined(value: unknown): value is null | undefined {
    return value === null || value === undefined
}

export function removeNullAndUndefined(obj: unknown, keepAsIs?: (key: string, parent: Record<string, unknown>) => boolean): unknown {
    if (isNullOrUndefined(obj)) {
        return undefined
    }

    if (Array.isArray(obj)) {
        const ar = obj
            .map(item => removeNullAndUndefined(item, keepAsIs))
            .filter((item): item is Exclude<typeof item, null | undefined> => isNullOrUndefined(item) === false)

        return ar.length > 0 ? ar : undefined
    }
    if (typeof obj === "object") {
        const record = obj as Record<string, unknown>
        const newObj: Record<string, unknown> = {}
        let hasValue = false
        for (const key in record) {
            const rawValue = record[key]
            if (isNullOrUndefined(rawValue)) {
                continue
            }
            if (keepAsIs?.(key, record)) {
                hasValue = true
                newObj[key] = rawValue
                continue
            }
            const newVal = removeNullAndUndefined(rawValue, keepAsIs)
            if (isNullOrUndefined(newVal)) {
                continue
            }
            hasValue = true
            newObj[key] = newVal
        }
        return hasValue ? newObj : undefined
    }
    return obj
}
