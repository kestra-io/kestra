import {computed, ref, toValue, watch, type MaybeRefOrGetter} from "vue"
import {State} from "@kestra-io/design-system"
import {useExecutionsStore, type Execution} from "../stores/executions"
import type {Log} from "../stores/logs"
import {handledIf} from "../utils/kestraHttp"
import {findFirstFailedTaskRun, taskRunPath} from "../utils/executionUtils"
import {stripBackticks} from "../utils/logs"
import {linkTagsAsText} from "../components/logs/linkify"

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
        const text = errorLogs.value.find((log) => log.taskRunId === failedTaskRun.value?.id)?.message
        return text ? stripBackticks(linkTagsAsText(text)) : undefined
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
            },
        }
    })

    async function loadErrorLogs(current: Execution, taskRunId: string) {
        errorLogs.value = []
        try {
            const response = await store.loadLogs({
                store: false,
                executionId: current.id,
                filters: [
                    {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
                    {field: "taskRunId", operation: "EQUALS", value: taskRunId},
                ],
            })
            if (toValue(execution)?.id === current.id && failedTaskRun.value?.id === taskRunId) errorLogs.value = response
        } catch (e: unknown) {
            handledIf(e, [403, 404])
        }
    }

    watch(
        [() => toValue(enabled), () => toValue(execution)?.id, () => failedTaskRun.value?.id],
        ([isEnabled]) => {
            const current = toValue(execution)
            const taskRunId = failedTaskRun.value?.id
            if (isEnabled && current && taskRunId) loadErrorLogs(current, taskRunId)
        },
        {immediate: true},
    )

    return {failedTaskRun, path, message, logsRoute}
}
