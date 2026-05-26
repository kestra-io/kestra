export function loopOver<T>(item: unknown, predicate: (item: unknown) => boolean, result: T[] = []): T[] {
    if (predicate(item)) {
        result.push(item as T)
    }

    if (Array.isArray(item)) {
        item.forEach(child => loopOver(child, predicate, result))
    } else if (item instanceof Object) {
        Object.entries(item).forEach(([_key, value]) => {
            loopOver(value, predicate, result)
        })
    }

    return result
}

export function findTaskById(flow: unknown, taskId: string): Record<string, unknown> | undefined {
    const result = loopOver<Record<string, unknown>>(flow, (value) => {
        if (value instanceof Object) {
            const obj = value as Record<string, unknown>
            if (obj.type !== undefined && obj.id === taskId) {
                return true
            }
        }

        return false
    })

    return result.length > 0 ? result[0] : undefined
}
