import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import type {ApiLoopRun, QueryFilter} from "@kestra-io/kestra-sdk"
import type {Execution} from "../stores/executions"
import {handled, type KestraHttpError} from "./kestraHttp"
import {iterationNumber, type LoopScopeEntry} from "./loopScope"

export const ITERATIONS_PAGE_SIZE = 50
export const CANDIDATE_PAGE_SIZE = 10
export const MAX_CANDIDATE_PAGES = 10

export type LoopIterationFailure = "forbidden" | "not-found" | "ambiguous" | "unknown"

export interface LoopIteration {
    id: string;
    number: number;
    value?: string;
    state: string;
    startDate?: string;
    endDate?: string;
    parentId?: string;
    taskId?: string;
    loopRun?: IterationLoopRun;
}

export interface LoopRoot {
    id: string;
    namespace: string;
    flowId: string;
    startDate?: string;
    endDate?: string;
    taskRunList?: TaskRunIdentity[];
}

export type TaskRunIdentity = {taskId: string}

export type IterationLoopRun = Partial<Pick<ApiLoopRun, "taskId" | "taskRunId" | "index" | "value">>

export interface IterationParent {
    parentId?: string;
    loopRun?: IterationLoopRun | null;
    taskRunList?: TaskRunIdentity[];
}

export interface FailedIterationChain {
    entries: LoopScopeEntry[];
    leafId: string;
}

export interface LoopIterationSearch {
    parentId?: string;
    root?: LoopRoot;
    taskId?: string;
    page?: number;
    size?: number;
    state?: string;
    excludeState?: string;
}

export interface LoopIterationPage {
    results: LoopIteration[];
    total: number;
}

export class LoopIterationError extends Error {
    readonly failure: LoopIterationFailure

    constructor(failure: LoopIterationFailure) {
        super(failure)
        this.failure = failure
    }
}

export function failureOf(error: unknown): LoopIterationFailure {
    const status = (error as KestraHttpError | undefined)?.status
    if (status === 403) return "forbidden"
    if (status === 404) return "not-found"
    return "unknown"
}

const toSecondPrecision = (milliseconds: number) => new Date(milliseconds).toISOString().replace(/\.\d+Z$/, "Z")

const floorToSecond = (date: string) => toSecondPrecision(Math.floor(Date.parse(date) / 1000) * 1000)

const ceilToSecond = (date: string) => {
    const floored = Math.floor(Date.parse(date) / 1000) * 1000
    return toSecondPrecision(/\.\d*[1-9]/.test(date) ? floored + 1000 : floored)
}

export function iterationWindowFilters(root: LoopRoot): QueryFilter[] {
    const filters: QueryFilter[] = [
        {field: "namespace", operation: "EQUALS", value: root.namespace},
        {field: "flowId", operation: "EQUALS", value: root.flowId},
    ]
    if (root.startDate) filters.push({field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: floorToSecond(root.startDate)})
    if (root.endDate) filters.push({field: "endDate", operation: "LESS_THAN_OR_EQUAL_TO", value: ceilToSecond(root.endDate)})
    return filters
}

export async function searchLoopIterations(search: LoopIterationSearch): Promise<LoopIterationPage> {
    const filters: QueryFilter[] = [
        {field: "kind", operation: "EQUALS", value: "LOOP"},
    ]
    if (search.taskId) filters.push({field: "taskId", operation: "EQUALS", value: search.taskId})
    if (search.parentId) filters.push({field: "parentId", operation: "EQUALS", value: search.parentId})
    if (search.root) filters.push(...iterationWindowFilters(search.root))
    if (search.state) filters.push({field: "state", operation: "IN", value: [search.state]})
    if (search.excludeState) filters.push({field: "state", operation: "NOT_IN", value: [search.excludeState]})

    try {
        const response = await ExecutionsAPI.searchExecutions(
            {page: search.page ?? 1, size: search.size ?? ITERATIONS_PAGE_SIZE, sort: search.root ? ["state.startDate:asc"] : ["loopRunIndex:asc", "id:asc"], filters},
        )
        return {
            results: (response.results ?? []).map((item) => ({
                id: item.id,
                number: iterationNumber(item.loopRun?.index ?? 0),
                value: item.loopRun?.value,
                state: item.state.current,
                startDate: item.state.startDate,
                endDate: item.state.endDate ?? undefined,
                parentId: item.parentId,
                taskId: item.loopRun?.taskId,
                loopRun: item.loopRun,
            })),
            total: response.total ?? 0,
        }
    } catch (error) {
        handled(error)
        throw new LoopIterationError(failureOf(error))
    }
}

export async function findIterationByNumber(parentId: string, taskId: string, number: number, taskRunId?: string): Promise<LoopIteration | undefined> {
    const matches: LoopIteration[] = []
    for (let page = Math.ceil(number / ITERATIONS_PAGE_SIZE); ; page++) {
        const {results, total} = await searchLoopIterations({parentId, taskId, page})
        matches.push(...results.filter((iteration) =>
            iteration.number === number && (taskRunId === undefined || iteration.loopRun?.taskRunId === taskRunId),
        ))
        const exhausted = results.length < ITERATIONS_PAGE_SIZE
            || page * ITERATIONS_PAGE_SIZE >= total
            || results.some((iteration) => iteration.number > number)
        if (exhausted || (taskRunId !== undefined && matches.length)) break
    }
    if (matches.length > 1) throw new LoopIterationError("ambiguous")
    return matches[0]
}

export async function findFirstFailedIteration(parentId: string, taskId: string, taskRunId: string): Promise<LoopIteration | undefined> {
    for (let page = 1; ; page++) {
        const {results, total} = await searchLoopIterations({parentId, taskId, state: "FAILED", page, size: CANDIDATE_PAGE_SIZE})
        const match = results.find((iteration) => iteration.loopRun?.taskRunId === taskRunId)
        if (match) return match
        if (results.length < CANDIDATE_PAGE_SIZE || page * CANDIDATE_PAGE_SIZE >= total) return undefined
    }
}

export async function loadIterationExecution(executionId: string): Promise<Execution> {
    try {
        return await ExecutionsAPI.execution({executionId}) as unknown as Execution
    } catch (error) {
        handled(error)
        throw new LoopIterationError(failureOf(error))
    }
}

export async function resolveIterationChain(
    candidate: LoopIteration & {taskId: string},
    rootId: string,
    fetchParent: (executionId: string) => Promise<IterationParent | undefined>,
    maxDepth = 8,
): Promise<FailedIterationChain | undefined> {
    const entries: LoopScopeEntry[] = [{taskId: candidate.taskId, number: candidate.number}]
    let parentId = candidate.parentId

    for (let depth = 0; parentId && parentId !== rootId && depth < maxDepth; depth++) {
        const parent = await fetchParent(parentId)
        if (parent?.loopRun?.taskId === undefined || parent.loopRun.index === undefined) return undefined
        entries.unshift({taskId: parent.loopRun.taskId, number: iterationNumber(parent.loopRun.index)})
        parentId = parent.parentId
    }

    return parentId === rootId ? {entries, leafId: candidate.id} : undefined
}

export interface FailedChainDeps {
    search: typeof searchLoopIterations;
    fetchParent: (executionId: string) => Promise<IterationParent | undefined>;
}

const defaultDeps: FailedChainDeps = {
    search: searchLoopIterations,
    fetchParent: loadIterationExecution,
}

export async function findFailedIterationChain(root: LoopRoot, taskId: string, deps: FailedChainDeps = defaultDeps): Promise<FailedIterationChain | undefined> {
    const parents = new Map<string, Promise<IterationParent | undefined>>()
    const fetchParent = (executionId: string) => {
        if (!parents.has(executionId)) parents.set(executionId, deps.fetchParent(executionId))
        return parents.get(executionId)!
    }

    for (let page = 1, seen = 0; page <= MAX_CANDIDATE_PAGES; page++) {
        const {results, total} = await deps.search({root, taskId, state: "FAILED", page, size: CANDIDATE_PAGE_SIZE})
        const chains = await Promise.all(results.map((candidate) =>
            resolveIterationChain({...candidate, taskId}, root.id, fetchParent).catch(() => undefined),
        ))
        const found = chains.find((chain) => chain !== undefined)
        seen += results.length
        if (found || !results.length || seen >= total) return found
    }
    return undefined
}
