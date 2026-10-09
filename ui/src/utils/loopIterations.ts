import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import type {Execution} from "../stores/executions"
import type {KestraHttpError, KestraRequestOptions} from "./kestraHttp"
import {iterationNumber} from "./loopScope"

export const ITERATIONS_PAGE_SIZE = 50

const QUIET: KestraRequestOptions = {showMessageOnError: false, ignoreNotFound: true}

export type LoopIterationFailure = "forbidden" | "not-found" | "unknown"

export interface LoopIteration {
    id: string;
    number: number;
    value?: string;
    state: string;
}

export interface LoopIterationSearch {
    parentId: string;
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
        {field: "parentId", operation: "EQUALS", value: search.parentId},
        {field: "kind", operation: "EQUALS", value: "LOOP"},
        {field: "taskId", operation: "EQUALS", value: search.taskId},
    ]
    if (search.state) filters.push({field: "state", operation: "IN", value: [search.state]})
    if (search.excludeState) filters.push({field: "state", operation: "NOT_IN", value: [search.excludeState]})

    try {
        const response = await ExecutionsAPI.searchExecutions(
            {page: search.page ?? 1, size: search.size ?? ITERATIONS_PAGE_SIZE, sort: ["loopRunIndex:asc"], filters},
            QUIET,
        )
        return {
            results: (response.results ?? []).map((item) => ({
                id: item.id,
                number: iterationNumber(item.loopRun?.index ?? 0),
                value: item.loopRun?.value,
                state: item.state.current,
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
