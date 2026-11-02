import {State, dayjs, type Dayjs} from "@kestra-io/design-system"
import type {Execution as ApiExecution, StateHistory, useClient} from "@kestra-io/kestra-sdk"
import type {RouteLocationRaw} from "vue-router"
import {apiUrl} from "override/utils/route"
import type {Execution} from "../stores/executions"

type KestraClient = ReturnType<typeof useClient>

const POLL_INTERVAL_MS = 300
const MAX_POLLS = 100

/**
 * Resolves with the execution once `predicate` accepts it, or with the last state seen after
 * {@link MAX_POLLS} polls so a caller never waits forever, and rejects when a poll request fails.
 */
export function waitFor($http: KestraClient, execution: {id: string}, predicate: (data: Execution) => boolean) {
    return new Promise((resolve, reject) => {
        let remaining = MAX_POLLS
        const poll = () => {
            $http.get(`${apiUrl()}/executions/${execution.id}`).then((response) => {
                remaining--
                if (predicate(response.data) || remaining <= 0) {
                    resolve(response.data)
                } else {
                    window.setTimeout(poll, POLL_INTERVAL_MS)
                }
            }, reject)
        }

        window.setTimeout(poll, POLL_INTERVAL_MS)
    })
}

export function findTaskRunsByState(execution: Execution, state: string)  {
    return (execution.taskRunList ?? []).filter((taskRun) => taskRun.state?.current === state)
}

export type CurrentStateExecution = Pick<ApiExecution, "state"> & {
    histories?: Array<StateHistory>
}

export function statePredicate(execution: Execution, current: CurrentStateExecution) {
    return (current.state.histories?.length ?? 0) >= (execution.state.histories?.length ?? 0)
}

export function waitForState($http: KestraClient, execution: Execution) {
    return waitFor($http, execution, (current) => {
        return statePredicate(execution, current)
    })
}

export function executionFileUrl(executionId: string | undefined, path: string): string {
    return `${apiUrl()}/executions/${executionId}/file?path=${encodeURIComponent(path)}`
}

const RELATIVE_TIME_RANGES = ["PT24H", "PT48H", "PT168H", "PT720H", "PT8760H"]

export type LoopParentExecution = Pick<ApiExecution, "id"> & {
    state?: Partial<Pick<ApiExecution["state"], "startDate" | "endDate">>;
}

export function loopIterationsRoute(
    parent: LoopParentExecution | undefined,
    taskId?: string,
    extraQuery: Record<string, string> = {},
    now?: Dayjs,
): RouteLocationRaw {
    return {
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": parent?.id,
            "filters[kind][EQUALS]": "LOOP",
            ...(taskId ? {"filters[taskId][EQUALS]": taskId} : {}),
            ...extraQuery,
            ...parentTimeBound(parent?.state, now),
        },
    }
}

type ExecutionTaskRun = NonNullable<Execution["taskRunList"]>[number]

const HIDDEN_TASK_TYPES = [
    "io.kestra.plugin.core.flow.ForEachItem$ForEachItemSplit",
    "io.kestra.plugin.core.flow.ForEachItem$ForEachItemMergeOutputs",
    "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable",
    "io.kestra.core.tasks.flows.ForEachItem$ForEachItemSplit",
    "io.kestra.core.tasks.flows.ForEachItem$ForEachItemMergeOutputs",
    "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable",
]

type FailureCandidate = {id: string; taskId: string; parentTaskRunId?: string | null; state?: {current?: string}}

export function isHiddenTaskType(taskType: string | undefined): boolean {
    return HIDDEN_TASK_TYPES.includes(taskType ?? "")
}

export function isFailedTaskRunState(state: string | undefined): boolean {
    return state === State.FAILED
}

export function firstFailedLeaf<T extends FailureCandidate>(
    taskRuns: T[],
    taskType: (taskId: string) => string | undefined = () => undefined,
): T | undefined {
    const failed = taskRuns.filter((taskRun) => isFailedTaskRunState(taskRun.state?.current) && !isHiddenTaskType(taskType(taskRun.taskId)))
    const parentIds = new Set(failed.map((taskRun) => taskRun.parentTaskRunId))
    return failed.find((taskRun) => !parentIds.has(taskRun.id))
}

export function findFirstFailedTaskRun(execution: Execution, taskType?: (taskId: string) => string | undefined): ExecutionTaskRun | undefined {
    return firstFailedLeaf(execution.taskRunList ?? [], taskType)
}

export function taskRunPath(execution: Execution, taskRun: ExecutionTaskRun): string[] {
    const byId = new Map((execution.taskRunList ?? []).map((candidate) => [candidate.id, candidate]))
    const path: string[] = []
    for (let current: ExecutionTaskRun | undefined = taskRun; current; current = current.parentTaskRunId ? byId.get(current.parentTaskRunId) : undefined) {
        path.unshift(current.value ? `${current.taskId} ${current.value}` : current.taskId)
    }
    return path
}

function parentTimeBound(state: LoopParentExecution["state"], now: Dayjs | undefined): Record<string, string> {
    if (!state?.startDate) {
        return {}
    }

    const startDate = dayjs(state.startDate).toISOString()
    if (state.endDate) {
        return {
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate,
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": dayjs(state.endDate).toISOString(),
        }
    }

    const age = (now ?? dayjs()).diff(startDate)
    const timeRange = RELATIVE_TIME_RANGES.find(range => dayjs.duration(range).asMilliseconds() > age)
    return timeRange
        ? {"filters[timeRange][EQUALS]": timeRange}
        : {"filters[startDate][GREATER_THAN_OR_EQUAL_TO]": startDate}
}
