import {ref, watch} from "vue"
import {useExecutionsStore} from "../../../stores/executions"
import {linkTagsAsText} from "../../logs/linkify"
import {handled} from "../../../utils/kestraHttp"

export function useTaskRunError(executionId: () => string, taskRunId: () => string) {
    const executionsStore = useExecutionsStore()
    const text = ref("")
    const loading = ref(false)

    let latestFetch = 0
    async function load() {
        const fetch = ++latestFetch
        text.value = ""
        loading.value = true
        try {
            const logs = await executionsStore
                .loadLogs({
                    store: false,
                    executionId: executionId(),
                    params: {"filters[taskRunId][EQUALS]": taskRunId(), "filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR"},
                })
                .catch((e: unknown) => {
                    handled(e)
                    return []
                })
            if (fetch !== latestFetch) return

            const withMessage = logs.filter(log => (log.message ?? "").length > 0)
            const errors = withMessage.filter(log => `${log.level}`.toUpperCase() === "ERROR")
            text.value = (errors.length > 0 ? errors : withMessage.slice(-1)).map(log => linkTagsAsText(log.message ?? "")).join("\n")
        } finally {
            if (fetch === latestFetch) loading.value = false
        }
    }

    watch([executionId, taskRunId], load, {immediate: true})

    return {text, loading}
}
