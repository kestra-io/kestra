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

export const LOG_PAGE_SIZE = 100
export const MAX_LOG_URL_LENGTH = 3500
export const CHAIN_CONCURRENCY = 6
const URL_PREFIX_MARGIN = 200
const CURSOR_MARGIN = 400
const FAILED_SEARCH_SIZE = 100
const MAX_CHAIN_DEPTH = 8
const EXECUTION_ID_FILTER_LENGTH = "&filters[executionId][IN]=".length

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

export interface ChainCache {
    fetched: Map<string, Promise<IterationNode | undefined>>;
    chains: Map<string, Promise<ScopeNode[] | undefined>>;
}

export interface CollectOptions {
    reserve?: number;
    limit?: number;
    cache?: ChainCache;
}

export const createChainCache = (): ChainCache => ({fetched: new Map(), chains: new Map()})

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

export function logUrlReserve(params: {levelParams: Record<string, string>; kinds: string[]; q?: string}): number {
    const others = [
        ...Object.entries(params.levelParams).map(([key, value]) => `&${key}=${encodeURIComponent(value)}`),
        `&filters[kind][IN]=${encodeURIComponent(params.kinds.join(","))}`,
        params.q ? `&filters[q][EQUALS]=${encodeURIComponent(params.q)}` : "",
        "&page=1&size=100&sort=timestamp:asc",
    ].join("")
    return URL_PREFIX_MARGIN + CURSOR_MARGIN + others.length
}

export const executionIdsUrlLength = (ids: Iterable<string>): number =>
    EXECUTION_ID_FILTER_LENGTH + encodeURIComponent([...ids].join(",")).length

async function mapLimit<T, R>(items: T[], limit: number, task: (item: T) => Promise<R>): Promise<R[]> {
    const results = new Array<R>(items.length)
    let next = 0
    const workers = Array.from({length: Math.min(limit, items.length)}, async () => {
        while (next < items.length) {
            const index = next++
            results[index] = await task(items[index])
        }
    })
    await Promise.all(workers)
    return results
}

function chainResolver(rootId: string, fetchIteration: LogTargetDeps["fetchIteration"], cache: ChainCache) {
    const fetchOnce = (id: string) => {
        if (!cache.fetched.has(id)) {
            const pending = fetchIteration(id)
            cache.fetched.set(id, pending)
            pending.catch(() => cache.fetched.delete(id))
        }
        return cache.fetched.get(id)!
    }

    function chainOf(node: IterationNode, depth: number): Promise<ScopeNode[] | undefined> {
        if (!cache.chains.has(node.id)) {
            const pending = resolve(node, depth)
            cache.chains.set(node.id, pending)
            pending.catch(() => cache.chains.delete(node.id))
        }
        return cache.chains.get(node.id)!
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

function withinBudget(chainsInOrder: ScopeNode[][], seed: Set<string>, options: CollectOptions) {
    const limit = options.limit ?? MAX_LOG_URL_LENGTH
    const reserve = options.reserve ?? 0
    const ids = new Set(seed)
    const kept: ScopeNode[][] = []
    for (const chain of chainsInOrder) {
        const candidate = new Set(ids)
        chain.forEach((node) => candidate.add(node.id))
        if (reserve + executionIdsUrlLength(candidate) > limit) break
        candidate.forEach((id) => ids.add(id))
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

export async function collectFailedTargets(root: LoopRoot, deps: LogTargetDeps = defaultDeps, options: CollectOptions = {}): Promise<LoopLogTargets> {
    const [topLevel, anyDepth] = await Promise.all([
        deps.search({parentId: root.id, state: "FAILED", size: FAILED_SEARCH_SIZE}),
        deps.search({root, state: "FAILED", size: FAILED_SEARCH_SIZE}),
    ])

    const candidates = [...new Map([...topLevel.results, ...anyDepth.results].map((item) => [item.id, item])).values()]
    const resolveChain = chainResolver(root.id, deps.fetchIteration, options.cache ?? createChainCache())
    const resolved = await mapLimit(candidates, CHAIN_CONCURRENCY, resolveChain)
    const chains = resolved.filter((chain): chain is ScopeNode[] => chain !== undefined)

    const {ids, kept} = withinBudget(chains, new Set([root.id]), options)
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

async function collectFailedDescendants(scope: ScopeNode[], deps: LogTargetDeps) {
    const chains: ScopeNode[][] = []
    let truncated = false
    let frontier: ScopeNode[][] = [scope]

    for (let depth = 0; frontier.length && depth < MAX_CHAIN_DEPTH; depth++) {
        const parents = frontier
        const pages = await mapLimit(parents, CHAIN_CONCURRENCY, (parent) =>
            deps.search({parentId: parent[parent.length - 1].id, state: "FAILED", size: FAILED_SEARCH_SIZE}),
        )
        frontier = []
        pages.forEach((page, index) => {
            if (page.total > page.results.length) truncated = true
            for (const child of page.results) {
                if (child.taskId === undefined) continue
                const chain = [...parents[index], {id: child.id, taskId: child.taskId, number: child.number, value: child.value}]
                chains.push(chain)
                frontier.push(chain)
            }
        })
    }
    return {chains, truncated}
}

export async function collectScopedTargets(
    root: LoopRoot,
    entries: LoopScopeEntry[],
    deps: LogTargetDeps = defaultDeps,
    options: CollectOptions = {},
): Promise<LoopLogTargets> {
    const scope = await resolveScope(root.id, entries, deps)
    const {chains: descendants, truncated: searchTruncated} = scope.length
        ? await collectFailedDescendants(scope, deps)
        : {chains: [], truncated: false}

    const {ids, kept} = withinBudget(descendants, new Set([root.id, ...scope.map((node) => node.id)]), options)

    return {
        executionIds: [...ids],
        chains: chainsById([...kept, scope]),
        failedChains: kept,
        failedShown: kept.length,
        truncated: searchTruncated || kept.length < descendants.length,
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
    q?: string;
    page: number;
    cursor?: string;
}

export async function searchMergedLogs(search: MergedLogSearch) {
    const filters: QueryFilter[] = [
        {field: "executionId", operation: "IN", value: search.executionIds},
        {field: "kind", operation: "IN", value: search.kinds},
        ...routeQueryToQueryFilters(search.levelParams),
    ]
    if (search.q) filters.push({field: "q", operation: "EQUALS", value: search.q})
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
