import {ref, computed} from "vue"
import {useExecutionsStore} from "../stores/executions"

export const LOOP_ITERATIONS_PAGE_SIZE = 10

export interface LoopIterationRow {
    id: string;
    value: string;
    index: number;
    state: {
        current: string;
        histories: Array<{state: string; date: string}>;
    };
}

export function useLoopIterations(parentExecutionId: string, taskId: string) {
    const executionsStore = useExecutionsStore()

    const iterations = ref<LoopIterationRow[]>([])
    const total = ref(0)
    const page = ref(1)
    const failedOnly = ref(false)
    const loading = ref(false)
    const loaded = ref(false)

    const hasMore = computed(() => iterations.value.length < total.value)
    const needsPreview = computed(() => total.value > LOOP_ITERATIONS_PAGE_SIZE)

    function toRow(execution: Record<string, unknown>): LoopIterationRow {
        const loopRun = execution.loopRun as { index: number; value: string } | undefined
        const state = execution.state as { current: string; histories: Array<{state: string; date: string}> }
        return {
            id: execution.id as string,
            value: loopRun?.value ?? "",
            index: loopRun?.index ?? 0,
            state,
        }
    }

    const error = ref<unknown>(undefined)

    let latestFetch = 0
    async function fetchPage(targetPage: number): Promise<void> {
        const fetch = ++latestFetch
        loading.value = true
        error.value = undefined
        try {
            const filters: Parameters<typeof executionsStore.findExecutions>[0] = {
                "filters[parentId][EQUALS]": parentExecutionId,
                "filters[kind][EQUALS]": "LOOP",
                "filters[taskId][EQUALS]": taskId,
                page: targetPage,
                size: LOOP_ITERATIONS_PAGE_SIZE,
                sort: "loopRunIndex:asc",
                commit: false,
            }
            if (failedOnly.value) {
                filters["filters[state][IN]"] = ["FAILED"]
            }

            const response = await executionsStore.findExecutions(filters)
            if (fetch !== latestFetch) return
            const rows = (response.results ?? []).map(toRow)

            iterations.value = targetPage === 1 ? rows : [...iterations.value, ...rows]
            total.value = response.total ?? 0
            page.value = targetPage
            loaded.value = true
        } catch (e) {
            if (fetch !== latestFetch) return
            error.value = e
        } finally {
            if (fetch === latestFetch) loading.value = false
        }
    }

    function ensureLoaded() {
        if (loaded.value || loading.value) return Promise.resolve()
        return fetchPage(1)
    }

    function loadMore() {
        if (!hasMore.value || loading.value) return Promise.resolve()
        return fetchPage(page.value + 1)
    }

    function retry() {
        if (loading.value) return Promise.resolve()
        return fetchPage(loaded.value ? page.value + 1 : 1)
    }

    function setFailedOnly(value: boolean) {
        failedOnly.value = value
        loaded.value = false
        iterations.value = []
        return fetchPage(1)
    }

    return {
        iterations,
        total,
        loading,
        error,
        loaded,
        hasMore,
        needsPreview,
        failedOnly,
        ensureLoaded,
        loadMore,
        retry,
        setFailedOnly,
    }
}
