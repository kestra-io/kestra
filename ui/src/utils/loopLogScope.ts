import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import {isLoopTaskType} from "@kestra-io/topology"
import {routeQueryToQueryFilters} from "./queryFilters"
import {
    findIterationByNumber,
    loadIterationExecution,
    LoopIterationError,
    searchLoopIterations,
    type IterationParent,
    type LoopIteration,
    type LoopRoot,
} from "./loopIterations"
import {iterationLabel, type LoopScopeEntry} from "./loopScope"

export const MAX_LOG_EXECUTION_IDS = 120
export const LOG_PAGE_SIZE = 100
const MAX_CHAIN_DEPTH = 8

export interface ScopeNode {
    id: string;
    taskId: string;
    number: number;
    value?: string;
}

export interface LoopLogTargets {
    executionIds: string[];
    chains: Record<string, ScopeNode[]>;
    failedChains: ScopeNode[][];
    failedShown: number;
    truncated: boolean;
    scope: ScopeNode[];
}

type IterationNode = IterationParent & {id: string}

export interface LogTargetDeps {
    search: typeof searchLoopIterations;
    fetchIteration: (executionId: string) => Promise<IterationNode | undefined>;
    findByNumber: typeof findIterationByNumber;
}

const defaultDeps: LogTargetDeps = {
    search: searchLoopIterations,
    fetchIteration: async (executionId) => (await loadIterationExecution(executionId)) as unknown as IterationNode,
    findByNumber: findIterationByNumber,
}

export function flowHasLoop(value: unknown): boolean {
    if (Array.isArray(value)) return value.some(flowHasLoop)
    if (value === null || typeof value !== "object") return false
    const record = value as Record<string, unknown>
    if (typeof record.type === "string" && isLoopTaskType(record.type)) return true
    return Object.values(record).some(flowHasLoop)
}

export const scopeLabel = (t: (key: string, named?: Record<string, unknown>) => string, chain: ScopeNode[]): string =>
    chain.map((node) => `${node.taskId}: ${iterationLabel(t, node.number, node.value)}`).join(" › ")

export const scopeEntriesOf = (chain: ScopeNode[]): LoopScopeEntry[] =>
    chain.map(({taskId, number}) => ({taskId, number}))

function chainResolver(rootId: string, fetchIteration: LogTargetDeps["fetchIteration"]) {
    const fetched = new Map<string, Promise<IterationNode | undefined>>()
    const chains = new Map<string, Promise<ScopeNode[] | undefined>>()

    const fetchOnce = (id: string) => {
        if (!fetched.has(id)) fetched.set(id, fetchIteration(id))
        return fetched.get(id)!
    }

    function chainOf(node: IterationNode, depth: number): Promise<ScopeNode[] | undefined> {
        if (!chains.has(node.id)) chains.set(node.id, resolve(node, depth))
        return chains.get(node.id)!
    }

    async function resolve(node: IterationNode, depth: number): Promise<ScopeNode[] | undefined> {
        const {taskId, index, value} = node.loopRun ?? {}
        if (taskId === undefined || index === undefined || depth > MAX_CHAIN_DEPTH) return undefined
        const own: ScopeNode = {id: node.id, taskId, number: index + 1, value}
        if (!node.parentId) return undefined
        if (node.parentId === rootId) return [own]

        const embedded = node.loopRun?.parent
        const parent = embedded?.id === node.parentId ? embedded as IterationNode : await fetchOnce(node.parentId)
        const parentChain = parent && await chainOf(parent, depth + 1)
        return parentChain && [...parentChain, own]
    }

    return (iteration: LoopIteration) => chainOf(
        {id: iteration.id, parentId: iteration.parentId, loopRun: iteration.loopRun},
        0,
    )
}

function withinBudget(chainsInOrder: ScopeNode[][], seed: Set<string>, maxIds: number) {
    const ids = new Set(seed)
    const kept: ScopeNode[][] = []
    for (const chain of chainsInOrder) {
        const missing = chain.filter((node) => !ids.has(node.id))
        if (ids.size + missing.length > maxIds) break
        missing.forEach((node) => ids.add(node.id))
        kept.push(chain)
    }
    return {ids, kept}
}

const chainsById = (chains: ScopeNode[][], into: Record<string, ScopeNode[]> = {}) => {
    for (const chain of chains) {
        chain.forEach((node, depth) => {
            into[node.id] = chain.slice(0, depth + 1)
        })
    }
    return into
}

export async function collectFailedTargets(root: LoopRoot, deps: LogTargetDeps = defaultDeps, maxIds = MAX_LOG_EXECUTION_IDS): Promise<LoopLogTargets> {
    const [topLevel, anyDepth] = await Promise.all([
        deps.search({parentId: root.id, state: "FAILED", size: maxIds}),
        deps.search({root, state: "FAILED", size: maxIds}),
    ])

    const candidates = [...new Map([...topLevel.results, ...anyDepth.results].map((item) => [item.id, item])).values()]
    const resolveChain = chainResolver(root.id, deps.fetchIteration)
    const resolved = await Promise.all(candidates.map((candidate) => resolveChain(candidate)))
    const chains = resolved.filter((chain): chain is ScopeNode[] => chain !== undefined)

    const {ids, kept} = withinBudget(chains, new Set([root.id]), maxIds)
    const searchedAll = topLevel.total <= topLevel.results.length && anyDepth.total <= anyDepth.results.length

    return {
        executionIds: [...ids],
        chains: chainsById(kept),
        failedChains: kept,
        failedShown: kept.length,
        truncated: kept.length < chains.length || !searchedAll,
        scope: [],
    }
}

export async function collectScopedTargets(
    root: LoopRoot,
    entries: LoopScopeEntry[],
    deps: LogTargetDeps = defaultDeps,
    maxIds = MAX_LOG_EXECUTION_IDS,
): Promise<LoopLogTargets> {
    const [failed, scope] = await Promise.all([collectFailedTargets(root, deps, maxIds), resolveScope(root.id, entries, deps)])
    const deepest = scope[scope.length - 1]
    const descendants = failed.failedChains.filter((chain) => chain.some((node) => node.id === deepest?.id))

    const {ids, kept} = withinBudget(descendants, new Set([root.id, ...scope.map((node) => node.id)]), maxIds)

    return {
        executionIds: [...ids],
        chains: chainsById([...kept, scope]),
        failedChains: kept,
        failedShown: kept.length,
        truncated: false,
        scope,
    }
}

async function resolveScope(rootId: string, entries: LoopScopeEntry[], deps: LogTargetDeps): Promise<ScopeNode[]> {
    const scope: ScopeNode[] = []
    let parentId = rootId
    for (const entry of entries) {
        const iteration = await deps.findByNumber(parentId, entry.taskId, entry.number)
        if (!iteration) throw new LoopIterationError("not-found")
        scope.push({id: iteration.id, taskId: entry.taskId, number: entry.number, value: iteration.value})
        parentId = iteration.id
    }
    return scope
}

export interface MergedLogSearch {
    executionIds: string[];
    kinds: string[];
    levelParams: Record<string, string>;
    page: number;
    cursor?: string;
}

export async function searchMergedLogs(search: MergedLogSearch) {
    const filters: QueryFilter[] = [
        {field: "executionId", operation: "IN", value: search.executionIds},
        {field: "kind", operation: "IN", value: search.kinds},
        ...routeQueryToQueryFilters(search.levelParams),
    ]
    const response = await LogsAPI.searchLogs(
        {page: search.page, size: LOG_PAGE_SIZE, sort: ["timestamp:asc"], cursor: search.cursor, filters},
        {showMessageOnError: false},
    )
    return {
        results: response.results ?? [],
        total: response.total ?? 0,
        nextCursor: response.nextCursor ?? undefined,
        cursorMode: response.type === "CURSOR",
    }
}
