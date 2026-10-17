import type {Task} from "@kestra-io/kestra-sdk"

// FIXME: any - recursive generic, complex object traversal
export function loopOver(item: any, predicate: (item: any) => boolean, result?: any[]): any[] { // FIXME: any
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

// A task is any object that has both `type` and `id`.
const isTask = (value: unknown): boolean =>
    value instanceof Object && (value as {type?: unknown}).type !== undefined && (value as {id?: unknown}).id !== undefined

export function findTaskById(flow: unknown, taskId: string): {type?: string; id?: string; [key: string]: unknown} | undefined {
    const result = loopOver(flow, (value) => {
        if (value instanceof Object) {
            if (value.type !== undefined && value.id === taskId) {
                return true
            }
        }

        return false
    })

    return result.length > 0 ? result[0] : undefined
}

// Task objects at any depth under `tasks` (flowable children, then/else, errors, cases...), parents included.
export function getAllTasks(tasks: unknown): Task[] {
    if (!tasks) return []
    return loopOver(tasks, isTask)
}

// Task ids from every executable section of a flow: tasks, errors, finally and after.
export function getAllTaskIds(flow: any): string[] {
    if (!flow) return []

    const result: any[] = []
    for (const section of [flow.tasks, flow.errors, flow.finally, flow.after]) {
        if (section) {
            loopOver(section, isTask, result)
        }
    }

    return [...new Set(result.map(task => task.id))]
}
