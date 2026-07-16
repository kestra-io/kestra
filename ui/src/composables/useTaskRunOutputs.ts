import {ref, watch, type ComputedRef, type Ref} from "vue"
import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"

// Since Kestra 2.0, task run outputs are no longer embedded on the Execution
// payload (`taskRun.outputs` is a deprecated pre-2.0 compatibility field) — they
// live behind the dedicated /outputs endpoints. This module fetches "which task
// runs have outputs" once per execution and shares the result across every
// caller asking about that execution, instead of one request per row.
const taskRunIdsWithOutputsCache = new Map<string, Promise<Set<string>>>()

function fetchTaskRunIdsWithOutputs(executionId: string, forceRefresh: boolean): Promise<Set<string>> {
    if (!forceRefresh) {
        const cached = taskRunIdsWithOutputsCache.get(executionId)
        if (cached) {
            return cached
        }
    }

    const pending = OutputsAPI.taskOutputsInformation(
        {executionId},
        {validateStatus: (status: number) => status === 200 || status === 404},
    ).then((data: any) => new Set<string>((data ?? []).map((task: any) => task.taskRunId).filter(Boolean)))

    taskRunIdsWithOutputsCache.set(executionId, pending)
    return pending
}

/**
 * Whether a given task run has outputs, per the dedicated outputs endpoint.
 * Bypasses the cache whenever the task run's own state changes, so a still-running
 * task correctly flips to "has outputs" once it finishes.
 */
export function useHasTaskRunOutputs(
    executionId: ComputedRef<string | undefined> | Ref<string | undefined>,
    taskRunId: ComputedRef<string | undefined> | Ref<string | undefined>,
    taskRunState: ComputedRef<string | undefined> | Ref<string | undefined>,
) {
    const hasOutputs = ref(false)
    let hasRunOnce = false

    watch(
        [executionId, taskRunId, taskRunState],
        async ([execId, runId]) => {
            if (!execId || !runId) {
                hasOutputs.value = false
                return
            }
            // Bypass the cache on any re-run after the first (e.g. the task run's own
            // state just changed) so a still-running task correctly flips to "has
            // outputs" once it finishes, instead of being stuck with a stale answer.
            const forceRefresh = hasRunOnce
            hasRunOnce = true
            const taskRunIds = await fetchTaskRunIdsWithOutputs(execId, forceRefresh)
            hasOutputs.value = taskRunIds.has(runId)
        },
        {immediate: true},
    )

    return hasOutputs
}

/** Fetches a single task run's output values. Returns an empty object when there are none. */
export async function loadTaskRunOutputs(executionId: string, taskRunId: string): Promise<Record<string, unknown>> {
    const data = await OutputsAPI.taskRunOutputs(
        {executionId, taskRunId},
        {validateStatus: (status: number) => status === 200 || status === 404},
    )
    return data ?? {}
}
