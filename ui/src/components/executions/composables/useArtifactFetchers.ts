import type {PagedResultsMetricEntry} from "@kestra-io/kestra-sdk"
import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"
import {callTaskRunEndpoint, loadTaskRunOutputs} from "../../../composables/useTaskRunOutputs"
import type {Execution} from "../../../stores/executions"

export function useArtifactFetchers(execution: () => Execution | undefined, selectedTaskRunId: () => string | undefined = () => undefined) {
    const runsOf = (taskId: string) => execution()?.taskRunList?.filter((run) => run.taskId === taskId) ?? []
    const defaultRunId = (taskId: string) => selectedTaskRunId() ?? runsOf(taskId).at(-1)?.id

    // Resolved when called, so an artifact opened on a still-running execution reads the latest run.
    const fetchOutputs = (taskId: string) => ({taskRunId}: {taskRunId?: string} = {}) => {
        const runId = taskRunId ?? defaultRunId(taskId)
        const executionId = execution()?.id
        return executionId && runId ? loadTaskRunOutputs(executionId, runId) : Promise.resolve({})
    }

    const fetchMetrics = (taskId: string) => ({page, size, sort, taskRunId}: {page?: number, size?: number, sort?: string, taskRunId?: string} = {}): Promise<PagedResultsMetricEntry> => {
        const executionId = execution()?.id
        if (!executionId) return Promise.resolve({results: [], total: 0})
        return MetricsAPI.searchByExecution({
            executionId,
            taskId,
            taskRunId: taskRunId ?? selectedTaskRunId(),
            page,
            size,
            sort: sort ? [sort] : undefined,
        })
    }

    const fetchEndpoint = (task: {id: string, type: string}) => (name: string, params: Record<string, string> = {}, {taskRunId}: {taskRunId?: string} = {}): Promise<unknown> => {
        const executionId = execution()?.id
        const runId = taskRunId ?? defaultRunId(task.id)
        if (!executionId || !runId) return Promise.reject(new Error("No task run to call the plugin endpoint for."))
        return callTaskRunEndpoint(task.type, name, executionId, runId, params)
    }

    return {runsOf, fetchOutputs, fetchMetrics, fetchEndpoint}
}
