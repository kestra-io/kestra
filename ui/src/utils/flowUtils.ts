import type {Task} from "@kestra-io/kestra-sdk"

export function loopOver<T>(
    item: unknown,
    predicate: (item: unknown) => item is T,
    result: T[] = [],
): T[] {
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

export type FlowTask = {type?: string; id?: string; [key: string]: unknown}

// A YAML `id: 123` parses as a number, and it is a valid task id.
function isTaskObject(value: unknown): value is Task {
    return value !== null && typeof value === "object" && "type" in value && "id" in value && typeof value.type === "string" && (typeof value.id === "string" || typeof value.id === "number")
}

export function findTaskById(flow: unknown, taskId: string): FlowTask | undefined {
    const result = loopOver(flow, (value): value is FlowTask => isTaskObject(value) && value.id === taskId)

    return result.length > 0 ? result[0] : undefined
}

// Task objects at any depth under `tasks` (flowable children, then/else, errors, cases...), parents included.
export function getAllTasks(tasks: unknown): Task[] {
    if (!tasks) return []
    return loopOver(tasks, isTaskObject)
}

export function getAllTaskIds(flow: unknown): string[] {
    if (!flow || typeof flow !== "object") return []
    const result: Task[] = []
    if ("tasks" in flow) {
        loopOver(flow.tasks, isTaskObject, result)
    }
    if ("errors" in flow) {
        loopOver(flow.errors, isTaskObject, result)
    }
    if ("finally" in flow) {
        loopOver(flow.finally, isTaskObject, result)
    }
    if ("afterExecution" in flow) {
        loopOver(flow.afterExecution, isTaskObject, result)
    }
    return [...new Set(result.map(t => String(t.id)))]
}

export const LOOP_TASK_TYPE = "io.kestra.plugin.core.flow.Loop"

export function loopTaskIds(flow: unknown): Set<string> {
    const result = loopOver(
        flow,
        (value): value is FlowTask => value !== null && typeof value === "object" && "type" in value && value.type === LOOP_TASK_TYPE,
    )
    return new Set(result.map(task => String(task.id)))
}
