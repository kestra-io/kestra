import {ref, computed} from "vue"
import {useExecutionsStore} from "../stores/executions"

/** Iterations are fetched this many at a time, at every depth, never more on a single load. */
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

/**
 * Fetches and paginates the iterations of a single Loop task run, identified by the execution
 * that contains it, the Loop's taskId, and (once you're inside an iteration that is itself
 * inside a Loop) that iteration's own execution id. Each nested Loop gets its own instance of
 * this composable, so depth is unbounded by construction rather than by an explicit limit.
 */
export function useLoopIterations(parentExecutionId: string, taskId: string) {
    const executionsStore = useExecutionsStore()

    const iterations = ref<LoopIterationRow[]>([])
    const total = ref(0)
    const page = ref(1)
    const failedOnly = ref(false)
    const loading = ref(false)
    const loaded = ref(false)

    const hasMore = computed(() => iterations.value.length < total.value)
    /** Below the page size, the tree is the whole answer: no preview footer, no drill-down. */
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

    async function fetchPage(targetPage: number): Promise<void> {
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
            const rows = (response.results ?? []).map(toRow)

            iterations.value = targetPage === 1 ? rows : [...iterations.value, ...rows]
            total.value = response.total ?? 0
            page.value = targetPage
            loaded.value = true
        } catch (e) {
            error.value = e
        } finally {
            loading.value = false
        }
    }

    /** First expand: loads page 1. No-op if already loaded, so re-expanding is free. */
    function ensureLoaded() {
        if (loaded.value || loading.value) return Promise.resolve()
        return fetchPage(1)
    }

    function loadMore() {
        if (!hasMore.value || loading.value) return Promise.resolve()
        return fetchPage(page.value + 1)
    }

    function setFailedOnly(value: boolean) {
        failedOnly.value = value
        loaded.value = false
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
        setFailedOnly,
    }
}
