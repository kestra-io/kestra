import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import {client} from "@kestra-io/kestra-sdk/client"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import {isLoopTaskType} from "@kestra-io/topology"
import {handled} from "./kestraHttp"
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
export const LOG_URL_MARGIN = URL_PREFIX_MARGIN + CURSOR_MARGIN
const FAILED_SEARCH_SIZE = 100
const MAX_CHAIN_DEPTH = 8
export const MAX_COLLECTION_REQUESTS = 60

export interface ScopeNode {
    id: string;
    taskId: string;
    number: number;
    value?: string;
    multiRun?: boolean;
}

export interface LoopLogTargets {
    executionIds: string[];
    chains: Record<string, ScopeNode[]>;
    failedChains: ScopeNode[][];
    scope: ScopeNode[];
    offset: number;
    end: number;
    total: number;
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
    maxRequests?: number;
    offset?: number;
}

export const createChainCache = (): ChainCache => ({fetched: new Map(), chains: new Map()})

const defaultDeps: LogTargetDeps = {
    search: searchLoopIterations,
    fetchIteration: loadIterationExecution,
    findByNumber: findIterationByNumber,
}

export function flowHasLoop(value: unknown): boolean {
    if (Array.isArray(value)) return value.some(flowHasLoop)
    if (value === null || typeof value !== "object") return false
    const record = value as Record<string, unknown>
    if (typeof record.type === "string" && isLoopTaskType(record.type)) return true
    return Object.values(record).some(flowHasLoop)
}

export const scopeLabel = (
    t: (key: string, named?: Record<string, unknown>) => string,
    chain: ScopeNode[],
    isAmbiguous: (node: ScopeNode) => boolean = () => false,
): string =>
    chain.map((node) => `${node.taskId}: ${iterationLabel(t, node.number, node.value, isAmbiguous(node))}`).join(" › ")

export const scopeEntriesOf = (chain: ScopeNode[]): LoopScopeEntry[] =>
    chain.map(({taskId, number}) => ({taskId, number}))

type LogFilterSearch = Pick<MergedLogSearch, "kinds" | "levelParams" | "q">

const executionIdsFilter = (executionIds: string[]): QueryFilter => ({field: "executionId", operation: "IN", value: executionIds})

function logFilters({kinds, levelParams, q}: LogFilterSearch): QueryFilter[] {
    const filters: QueryFilter[] = [{field: "kind", operation: "IN", value: kinds}, ...routeQueryToQueryFilters(levelParams)]
    if (q) filters.push({field: "q", operation: "EQUALS", value: q})
    return filters
}

const serializedQueryLength = (query: Record<string, unknown>) => {
    const url = client.buildUrl({baseUrl: "", url: "/", query})
    return url.length - url.indexOf("?") - 1
}

export function logUrlReserve(search: LogFilterSearch): number {
    const query = {filters: logFilters(search), page: 1, size: LOG_PAGE_SIZE, sort: ["timestamp:asc"]}
    return LOG_URL_MARGIN + serializedQueryLength(query) + 1
}

export const executionIdsUrlLength = (ids: Iterable<string>): number => {
    const executionIds = [...ids]
    return executionIds.length ? serializedQueryLength({filters: [executionIdsFilter(executionIds)]}) : 0
}

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

const withMultiRun = (node: ScopeNode, hostTaskRuns: IterationParent["taskRunList"]): ScopeNode =>
    (hostTaskRuns?.filter((run) => run.taskId === node.taskId).length ?? 0) > 1 ? {...node, multiRun: true} : node

function chainResolver(root: LoopRoot, fetchIteration: LogTargetDeps["fetchIteration"], cache: ChainCache) {
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
        if (node.parentId === root.id) return [withMultiRun(own, root.taskRunList)]

        const parent = await fetchOnce(node.parentId)
        const parentChain = parent && await chainOf(parent, depth + 1)
        return parentChain && [...parentChain, withMultiRun(own, parent.taskRunList)]
    }

    return (iteration: LoopIteration) => chainOf(
        {id: iteration.id, parentId: iteration.parentId, loopRun: iteration.loopRun},
        0,
    )
}

function fitsBudget(ids: Set<string>, chain: ScopeNode[], options: CollectOptions) {
    const candidate = new Set(ids)
    chain.forEach((node) => candidate.add(node.id))
    return (options.reserve ?? 0) + executionIdsUrlLength(candidate) <= (options.limit ?? MAX_LOG_URL_LENGTH)
}

const chainsById = (chains: ScopeNode[][], into: Record<string, ScopeNode[]> = {}) => {
    for (const chain of chains) {
        chain.forEach((node, depth) => {
            into[node.id] = chain.slice(0, depth + 1)
        })
    }
    return into
}

class RequestBudgetExceeded extends Error {}

function budgeted(deps: LogTargetDeps, maxRequests: number): LogTargetDeps {
    let remaining = maxRequests
    const spend = () => {
        if (remaining <= 0) throw new RequestBudgetExceeded()
        remaining--
    }
    return {
        ...deps,
        search: (search) => {
            spend()
            return deps.search(search)
        },
        fetchIteration: (id) => {
            spend()
            return deps.fetchIteration(id)
        },
    }
}

interface ChainResolution {
    chain?: ScopeNode[];
    deferred: boolean;
}

function resolveChains(candidates: LoopIteration[], root: LoopRoot, deps: LogTargetDeps, cache: ChainCache): Promise<ChainResolution[]> {
    const resolveChain = chainResolver(root, deps.fetchIteration, cache)
    return mapLimit(candidates, CHAIN_CONCURRENCY, (candidate) =>
        resolveChain(candidate).then(
            (chain) => ({chain, deferred: false}),
            (error) => {
                if (error instanceof RequestBudgetExceeded) return {deferred: true}
                throw error
            },
        ),
    )
}

async function collectBatch(
    root: LoopRoot,
    searchRoot: LoopRoot,
    seed: string[],
    isTarget: (chain: ScopeNode[]) => boolean,
    deps: LogTargetDeps,
    options: CollectOptions,
) {
    const limited = budgeted(deps, options.maxRequests ?? MAX_COLLECTION_REQUESTS)
    const cache = options.cache ?? createChainCache()
    const offset = options.offset ?? 0
    const ids = new Set(seed)
    const kept: ScopeNode[][] = []
    let position = offset
    let total = 0
    let cut = false

    try {
        do {
            const page = Math.floor(position / FAILED_SEARCH_SIZE) + 1
            const result = await limited.search({root: searchRoot, state: "FAILED", page, size: FAILED_SEARCH_SIZE})
            total = result.total
            const candidates = result.results.slice(position - (page - 1) * FAILED_SEARCH_SIZE)
            if (!candidates.length) {
                position = Math.max(position, total)
                break
            }

            const resolutions = await resolveChains(candidates, root, limited, cache)
            for (const {chain, deferred} of resolutions) {
                if (deferred) {
                    cut = true
                    break
                }
                if (chain && isTarget(chain)) {
                    if (kept.length && !fitsBudget(ids, chain, options)) {
                        cut = true
                        break
                    }
                    chain.forEach((node) => ids.add(node.id))
                    kept.push(chain)
                }
                position++
            }
        } while (!cut && !kept.length && position < total)
    } catch (error) {
        if (!(error instanceof RequestBudgetExceeded)) throw error
    }

    return {ids, kept, offset, end: position, total}
}

export function collectFailedTargets(root: LoopRoot, deps: LogTargetDeps = defaultDeps, options: CollectOptions = {}): Promise<LoopLogTargets> {
    return collectBatch(root, root, [root.id], () => true, deps, options).then(({ids, kept, ...batch}) => ({
        executionIds: [...ids],
        chains: chainsById(kept),
        failedChains: kept,
        scope: [],
        ...batch,
    }))
}

export async function collectScopedTargets(
    root: LoopRoot,
    entries: LoopScopeEntry[],
    deps: LogTargetDeps = defaultDeps,
    options: CollectOptions = {},
): Promise<LoopLogTargets> {
    const {scope, deepest} = await resolveScope(root.id, entries, deps)
    const offset = options.offset ?? 0
    if (!deepest) {
        return {executionIds: [root.id], chains: chainsById([scope]), failedChains: [], scope, offset, end: offset, total: 0}
    }

    const searchRoot = {...root, startDate: deepest.startDate ?? root.startDate, endDate: deepest.endDate ?? root.endDate}
    const isDescendant = (chain: ScopeNode[]) => chain.length > scope.length && chain.some((node) => node.id === deepest.id)
    const {ids, kept, ...batch} = await collectBatch(root, searchRoot, [root.id, ...scope.map((node) => node.id)], isDescendant, deps, options)

    return {
        executionIds: [...ids],
        chains: chainsById([...kept, scope]),
        failedChains: kept,
        scope,
        ...batch,
    }
}

async function resolveScope(rootId: string, entries: LoopScopeEntry[], deps: LogTargetDeps) {
    const scope: ScopeNode[] = []
    let deepest: LoopIteration | undefined
    let parentId = rootId
    for (const entry of entries) {
        const iteration = await deps.findByNumber(parentId, entry.taskId, entry.number)
        if (!iteration) throw new LoopIterationError("not-found")
        scope.push({id: iteration.id, taskId: entry.taskId, number: entry.number, value: iteration.value})
        deepest = iteration
        parentId = iteration.id
    }
    return {scope, deepest}
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
    const filters = [executionIdsFilter(search.executionIds), ...logFilters(search)]
    const response = await LogsAPI.searchLogs(
        {page: search.page, size: LOG_PAGE_SIZE, sort: ["timestamp:asc"], cursor: search.cursor, filters},
    ).catch((error: unknown) => {
        handled(error)
        throw error
    })
    return {
        results: response.results ?? [],
        total: response.total ?? 0,
        nextCursor: response.nextCursor ?? undefined,
        cursorMode: response.type === "CURSOR",
    }
}
