import {computed, ref, toValue, watch, type MaybeRefOrGetter} from "vue"
import {State} from "@kestra-io/design-system"
import {useExecutionsStore, type Execution} from "../stores/executions"
import type {Log} from "../stores/logs"
import {handledIf} from "../utils/kestraHttp"
import {findFirstFailedTaskRun, taskRunPath} from "../utils/executionUtils"
import {stripBackticks} from "../utils/logs"

export function useFirstFailure(execution: MaybeRefOrGetter<Execution | undefined>, enabled: MaybeRefOrGetter<boolean> = true) {
    const store = useExecutionsStore()

    const errorLogs = ref<Log[]>([])

    const failedTaskRun = computed(() => {
        const current = toValue(execution)
        return current?.state.current === State.FAILED ? findFirstFailedTaskRun(current) : undefined
    })

    const path = computed(() => {
        const current = toValue(execution)
        return current && failedTaskRun.value ? taskRunPath(current, failedTaskRun.value) : []
    })

    const message = computed(() => {
        const taskLog = errorLogs.value.find((log) => log.taskRunId === failedTaskRun.value?.id)
        const text = (taskLog ?? errorLogs.value.at(-1))?.message
        return text ? stripBackticks(text) : undefined
    })

    const logsRoute = computed(() => {
        const current = toValue(execution)
        return {
            name: "executions/update/logs",
            params: {
                tenantId: current?.tenantId,
                id: current?.id,
                namespace: current?.namespace,
                flowId: current?.flowId,
            },
            query: {
                "filters[taskRunId][EQUALS]": failedTaskRun.value?.id,
                "filters[kind][IN]": current?.kind,
            },
        }
    })

    async function loadErrorLogs(current: Execution) {
        try {
            const response = await store.loadLogs({
                store: false,
                executionId: current.id,
                params: {
                    "filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR",
                    "filters[kind][IN]": current.kind,
                },
            })
            errorLogs.value = response
        } catch (e: unknown) {
            handledIf(e, [403, 404])
        }
    }

    watch(
        () => [toValue(enabled), toValue(execution)?.id, failedTaskRun.value?.id] as const,
        ([isEnabled]) => {
            const current = toValue(execution)
            if (isEnabled && current && failedTaskRun.value) loadErrorLogs(current)
        },
        {immediate: true},
    )

    return {failedTaskRun, path, message, logsRoute}
}
