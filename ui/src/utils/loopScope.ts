export const LOOP_SCOPE_QUERY_KEY = "loopScope"

export interface LoopScopeEntry {
    taskId: string;
    number: number;
}

export const iterationNumber = (loopRunIndex: number) => loopRunIndex + 1

export const loopRunIndexOf = (number: number) => number - 1

export function parseLoopScope(raw: unknown): LoopScopeEntry[] {
    const value = Array.isArray(raw) ? raw[0] : raw
    if (typeof value !== "string" || !value) return []

    const entries: LoopScopeEntry[] = []
    for (const part of value.split(",")) {
        const separator = part.lastIndexOf(":")
        const taskId = part.slice(0, separator)
        const number = Number(part.slice(separator + 1))
        if (separator <= 0 || !Number.isInteger(number) || number < 1) break
        entries.push({taskId, number})
    }
    return entries
}

export const serializeLoopScope = (entries: LoopScopeEntry[]): string | undefined =>
    entries.length ? entries.map(({taskId, number}) => `${taskId}:${number}`).join(",") : undefined

export function withScopedIteration(entries: LoopScopeEntry[], depth: number, taskId: string, number: number): LoopScopeEntry[] {
    return [...entries.slice(0, depth), {taskId, number}]
}

export function scopedExecutionId(nodeUid: string, executionsByPath: Record<string, {id?: string}>): string | undefined {
    const path = Object.keys(executionsByPath)
        .filter((candidate) => nodeUid.startsWith(`${candidate}.`))
        .sort((a, b) => b.length - a.length)[0]
    return path ? executionsByPath[path]?.id : undefined
}
