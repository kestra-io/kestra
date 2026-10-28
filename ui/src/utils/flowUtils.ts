import type {Task} from "@kestra-io/kestra-sdk"

export function loopOver<T = Record<string, unknown>>(
    item: unknown,
    predicate: (item: any) => boolean,
    result: T[] = [],
): T[] {
    if (predicate(item)) {
        result.push(item as T)
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

export type FlowTask = {type?: string; id?: string; [key: string]: unknown}

function isTaskObject(value: unknown): value is FlowTask {
    return value !== null && typeof value === "object" && "type" in value && "id" in value && value.type !== undefined && value.id !== undefined
}

export function findTaskById(flow: unknown, taskId: string): FlowTask | undefined {
    const result = loopOver<FlowTask>(flow, (value) => {
        return value !== null && typeof value === "object" && "id" in value && "type" in value && value.id === taskId && value.type !== undefined
    })

    return result.length > 0 ? result[0] : undefined
}

// Task objects at any depth under `tasks` (flowable children, then/else, errors, cases...), parents included.
export function getAllTasks(tasks: unknown): Task[] {
    if (!tasks) return []
    return loopOver<Task>(tasks, isTaskObject)
}

export function getAllTaskIds(flow: unknown): string[] {
    if (!flow || typeof flow !== "object") return []
    const flowObj = flow as { tasks?: unknown; errors?: unknown }
    const result: FlowTask[] = []
    if (flowObj.tasks) {
        loopOver<FlowTask>(flowObj.tasks, isTaskObject, result)
    }
    if (flowObj.errors) {
        loopOver<FlowTask>(flowObj.errors, isTaskObject, result)
    }
    return [...new Set(result.map(t => String(t.id)))]
}

export const LOOP_TASK_TYPE = "io.kestra.plugin.core.flow.Loop"

export function loopTaskIds(flow: unknown): Set<string> {
    const result = loopOver<FlowTask>(
        flow,
        (value) => value !== null && typeof value === "object" && "type" in value && value.type === LOOP_TASK_TYPE,
    )
    return new Set(result.map(task => String(task.id)))
}
