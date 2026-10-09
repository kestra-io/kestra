import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import type {Execution} from "../stores/executions"
import type {KestraHttpError, KestraRequestOptions} from "./kestraHttp"
import {iterationNumber, type LoopScopeEntry} from "./loopScope"

export const ITERATIONS_PAGE_SIZE = 50

const QUIET: KestraRequestOptions = {showMessageOnError: false, ignoreNotFound: true}

export type LoopIterationFailure = "forbidden" | "not-found" | "unknown"

export interface LoopIteration {
    id: string;
    number: number;
    value?: string;
    state: string;
    parentId?: string;
}

export interface LoopRoot {
    id: string;
    namespace: string;
    flowId: string;
    startDate?: string;
}

export interface IterationParent {
    parentId?: string;
    loopRun?: {taskId?: string; index?: number};
}

export interface FailedIterationChain {
    entries: LoopScopeEntry[];
    leafId: string;
}

export interface LoopIterationSearch {
    parentId?: string;
    root?: LoopRoot;
    taskId: string;
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

export async function searchLoopIterations(search: LoopIterationSearch): Promise<LoopIterationPage> {
    const filters: QueryFilter[] = [
        {field: "kind", operation: "EQUALS", value: "LOOP"},
        {field: "taskId", operation: "EQUALS", value: search.taskId},
    ]
    if (search.parentId) filters.push({field: "parentId", operation: "EQUALS", value: search.parentId})
    if (search.root) {
        filters.push({field: "namespace", operation: "EQUALS", value: search.root.namespace})
        filters.push({field: "flowId", operation: "EQUALS", value: search.root.flowId})
        if (search.root.startDate) {
            filters.push({field: "startDate", operation: "GREATER_THAN_OR_EQUAL_TO", value: search.root.startDate.replace(/\.\d+/, "")})
        }
    }
    if (search.state) filters.push({field: "state", operation: "IN", value: [search.state]})
    if (search.excludeState) filters.push({field: "state", operation: "NOT_IN", value: [search.excludeState]})

    try {
        const response = await ExecutionsAPI.searchExecutions(
            {page: search.page ?? 1, size: search.size ?? ITERATIONS_PAGE_SIZE, sort: [search.root ? "state.startDate:asc" : "loopRunIndex:asc"], filters},
            QUIET,
        )
        return {
            results: (response.results ?? []).map((item) => ({
                id: item.id,
                number: iterationNumber(item.loopRun?.index ?? 0),
                value: item.loopRun?.value,
                state: item.state.current,
                parentId: item.parentId,
            })),
            total: response.total ?? 0,
        }
    } catch (error) {
        throw new LoopIterationError(failureOf(error))
    }
}

export async function findIterationByNumber(parentId: string, taskId: string, number: number): Promise<LoopIteration | undefined> {
    const page = Math.ceil(number / ITERATIONS_PAGE_SIZE)
    const {results} = await searchLoopIterations({parentId, taskId, page})
    return results.find((iteration) => iteration.number === number)
}

export async function findFirstFailedIteration(parentId: string, taskId: string): Promise<LoopIteration | undefined> {
    const {results} = await searchLoopIterations({parentId, taskId, state: "FAILED", size: 1})
    return results[0]
}

export async function loadIterationExecution(executionId: string): Promise<Execution> {
    try {
        return await ExecutionsAPI.execution({executionId}, QUIET) as unknown as Execution
    } catch (error) {
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

export async function findFailedIterationChain(root: LoopRoot, taskId: string): Promise<FailedIterationChain | undefined> {
    const {results} = await searchLoopIterations({root, taskId, state: "FAILED", size: 10})
    const fetchParent = async (executionId: string) => (await loadIterationExecution(executionId)) as IterationParent

    for (const candidate of results) {
        const chain = await resolveIterationChain({...candidate, taskId}, root.id, fetchParent).catch(() => undefined)
        if (chain) return chain
    }
    return undefined
}
