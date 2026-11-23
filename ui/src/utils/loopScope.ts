export const LOOP_SCOPE_QUERY_KEY = "loopScope"

export interface LoopScopeEntry {
    taskId: string;
    number: number;
}

export const iterationNumber = (loopRunIndex: number) => loopRunIndex + 1

export const loopRunIndexOf = (number: number) => number - 1

const INDEX_SEPARATOR = "@"
const LEGACY_NUMBER_SEPARATOR = ":"

function parseEntry(part: string): LoopScopeEntry | undefined {
    const separator = Math.max(part.lastIndexOf(INDEX_SEPARATOR), part.lastIndexOf(LEGACY_NUMBER_SEPARATOR))
    const taskId = part.slice(0, separator)
    const position = Number(part.slice(separator + 1))
    if (separator <= 0 || part.slice(separator + 1) === "" || !Number.isInteger(position)) return undefined
    const number = part[separator] === INDEX_SEPARATOR ? iterationNumber(position) : position
    return number >= 1 ? {taskId, number} : undefined
}

export function parseLoopScope(raw: unknown): LoopScopeEntry[] {
    const value = Array.isArray(raw) ? raw[0] : raw
    if (typeof value !== "string" || !value) return []

    const entries: LoopScopeEntry[] = []
    for (const part of value.split(",")) {
        const entry = parseEntry(part)
        if (!entry) break
        entries.push(entry)
    }
    return entries
}

export const serializeLoopScope = (entries: LoopScopeEntry[]): string | undefined =>
    entries.length ? entries.map(({taskId, number}) => `${taskId}${INDEX_SEPARATOR}${loopRunIndexOf(number)}`).join(",") : undefined

export function withScopedIteration(entries: LoopScopeEntry[], depth: number, taskId: string, number: number): LoopScopeEntry[] {
    return [...entries.slice(0, depth), {taskId, number}]
}

export function deepestEnclosingPath(nodeUid: string, paths: Iterable<string>): string | undefined {
    return [...paths]
        .filter((candidate) => nodeUid.startsWith(`${candidate}.`))
        .sort((a, b) => b.length - a.length)[0]
}

export function scopedExecutionId(nodeUid: string, executionsByPath: Record<string, {id?: string}>, subflowPaths: string[] = []): string | undefined {
    const path = deepestEnclosingPath(nodeUid, [...Object.keys(executionsByPath), ...subflowPaths])
    return path && !subflowPaths.includes(path) ? executionsByPath[path]?.id : undefined
}

export interface LoopScopeTrailEntry extends LoopScopeEntry {
    value?: string;
}

type Translate = (key: string, named?: Record<string, unknown>) => string

export function iterationLabel(t: Translate, number: number, value?: string, ambiguous = false): string {
    const index = loopRunIndexOf(number)
    if (!value?.trim()) return t("topology-graph.loop.iteration-index", {index})
    return ambiguous ? t("topology-graph.loop.iteration-value-index", {value, index}) : value
}

export interface LabelledIteration {
    id: string;
    parentId?: string;
    taskId?: string;
    value?: string;
}

type ValueIdentity = Pick<LabelledIteration, "parentId" | "taskId" | "value">

const valueKey = ({parentId, taskId, value}: ValueIdentity) => JSON.stringify([parentId ?? "", taskId ?? "", value ?? ""])

export function ambiguousValues(iterations: LabelledIteration[]): (iteration: ValueIdentity) => boolean {
    const idsByValue = new Map<string, Set<string>>()
    for (const iteration of iterations) {
        const key = valueKey(iteration)
        idsByValue.set(key, (idsByValue.get(key) ?? new Set()).add(iteration.id))
    }
    return (iteration) => (idsByValue.get(valueKey(iteration))?.size ?? 0) > 1
}
