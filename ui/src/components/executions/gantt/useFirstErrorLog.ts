import {ref, watch, type Ref} from "vue"
import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import type {QueryFilter} from "@kestra-io/kestra-sdk"

export function useFirstErrorLog(executionId: Ref<string>, taskRunId: Ref<string | undefined>) {
    const message = ref<string | undefined>(undefined)

    let latestFetch = 0
    async function load() {
        const fetch = ++latestFetch
        message.value = undefined
        if (!taskRunId.value) return

        const filters: QueryFilter[] = [
            {field: "executionId", operation: "EQUALS", value: executionId.value},
            {field: "taskRunId", operation: "EQUALS", value: taskRunId.value},
            {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
        ]
        try {
            const response = await LogsAPI.searchLogs({page: 1, size: 1, sort: ["timestamp:asc"], filters})
            if (fetch !== latestFetch) return
            message.value = response.results?.[0]?.message
        } catch {
            if (fetch === latestFetch) message.value = undefined
        }
    }

    watch([executionId, taskRunId], load, {immediate: true})

    return {message}
}
