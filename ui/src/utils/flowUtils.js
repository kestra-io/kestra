export function loopOver(item, predicate, result) {
    if (result === undefined) {
        result = []
    }

    if (predicate(item)) {
        result.push(item)
    }

    if (Array.isArray(item)) {
        item.flatMap(child => loopOver(child, predicate, result))
    } else if (item instanceof Object) {
        Object.entries(item).flatMap(([_key, value]) => {
            loopOver(value, predicate, result)
        })
    }

    return result
}

export function findTaskById(flow, taskId) {
    let result = loopOver(flow, (value) => {
        if (value instanceof Object) {
            if (value.type !== undefined && value.id === taskId) {
                return true
            }
        }

        return false
    })

    return result.length > 0 ? result[0] : undefined
}
