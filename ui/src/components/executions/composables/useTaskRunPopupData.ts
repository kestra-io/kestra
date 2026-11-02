import {computed, ref, shallowRef, watch, type Ref} from "vue"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import * as MetricsAPI from "@kestra-io/kestra-sdk/metrics"
import {loadTaskRunOutputs} from "../../../composables/useTaskRunOutputs"
import {defaultTaskRunLogLevel} from "../taskRunLogLevel"

export type LoadStatus = "idle" | "loading" | "ready" | "error"

export interface Loadable<T> {
    status: Ref<LoadStatus>
    value: Ref<T>
}

function useLoadable<T>(key: () => string | undefined, load: () => Promise<T>, empty: T): Loadable<T> {
    const status = ref<LoadStatus>("idle")
    const value = shallowRef<T>(empty) as Ref<T>
    let sequence = 0

    watch(key, async (current) => {
        const request = ++sequence
        if (current === undefined) {
            status.value = "idle"
            return
        }
        status.value = "loading"
        try {
            const result = await load()
            if (request !== sequence) return
            value.value = result
            status.value = "ready"
        } catch (error) {
            if (request !== sequence) return
            console.error("[TaskRunPopup] could not load task run data", error)
            value.value = empty
            status.value = "error"
        }
    }, {immediate: true})

    return {status, value}
}

interface PopupDataSource {
    enabled: () => boolean
    executionId: () => string | undefined
    taskId: () => string
    taskRunId: () => string | undefined
    logTaskRunId: () => string
    logAttemptNumber: () => number
    logChildTaskRunIds: () => string[]
    taskRunState: () => string | undefined
}

export function useTaskRunPopupData(source: PopupDataSource) {
    const reloads = ref(0)

    const baseKey = computed(() => {
        const executionId = source.executionId()
        const taskRunId = source.taskRunId()
        if (!source.enabled() || !executionId || !taskRunId) return undefined
        return [executionId, taskRunId, source.taskRunState(), reloads.value].join("|")
    })

    const metricsCount = useLoadable(
        () => baseKey.value,
        async () => {
            const page = await MetricsAPI.searchByExecution({
                executionId: source.executionId() as string,
                taskId: source.taskId(),
                taskRunId: source.taskRunId(),
                page: 1,
                size: 1,
            })
            return page.total ?? 0
        },
        0,
    )

    const logsCount = useLoadable(
        () => baseKey.value && `${baseKey.value}|${source.logAttemptNumber()}`,
        async () => {
            const countLogs = (filters: QueryFilter[]) => LogsAPI.searchLogs({
                filters: [
                    {field: "executionId", operation: "EQUALS", value: source.executionId() as string},
                    {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: defaultTaskRunLogLevel()},
                    ...filters,
                ],
                page: 1,
                size: 1,
            }).then((page) => page.total ?? page.results.length)

            const childTaskRunIds = source.logChildTaskRunIds()
            const counts = await Promise.all([
                countLogs([
                    {field: "taskRunId", operation: "EQUALS", value: source.logTaskRunId()},
                    {field: "attemptNumber", operation: "EQUALS", value: String(source.logAttemptNumber())},
                ]),
                ...(childTaskRunIds.length ? [countLogs([{field: "taskRunId", operation: "IN", value: childTaskRunIds}])] : []),
            ])
            return counts.reduce((sum, count) => sum + count, 0)
        },
        0,
    )

    const outputs = useLoadable(
        () => baseKey.value,
        () => loadTaskRunOutputs(source.executionId() as string, source.taskRunId() as string),
        {} as Record<string, unknown>,
    )

    const outputsCount = computed(() => Object.keys(outputs.value.value).length)

    return {
        metricsCount,
        logsCount,
        outputs,
        outputsCount,
        reload: () => reloads.value++,
    }
}
