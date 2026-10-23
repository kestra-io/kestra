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

export function loopScopeFilters(parentExecutionId: string, taskId: string) {
    return {
        "filters[parentId][EQUALS]": parentExecutionId,
        "filters[kind][EQUALS]": "LOOP",
        "filters[taskId][EQUALS]": taskId,
    }
}

export function toLoopIterationRow(execution: Record<string, unknown>): LoopIterationRow {
    const loopRun = execution.loopRun as { index: number; value: string } | undefined
    const state = execution.state as { current: string; histories: Array<{state: string; date: string}> }
    return {
        id: execution.id as string,
        value: loopRun?.value ?? "",
        index: loopRun?.index ?? 0,
        state,
    }
}

export function useLoopIterations(parentExecutionId: string, taskId: string, options: {failedOnly?: boolean} = {}) {
    const executionsStore = useExecutionsStore()

    const iterations = ref<LoopIterationRow[]>([])
    const total = ref(0)
    const page = ref(1)
    const failedOnly = ref(options.failedOnly ?? false)
    const loading = ref(false)
    const loaded = ref(false)
    const totalCount = ref<number | undefined>(undefined)
    const failedCount = ref<number | undefined>(undefined)

    const hasMore = computed(() => iterations.value.length < total.value)
    const needsPreview = computed(() => total.value > LOOP_ITERATIONS_PAGE_SIZE)

    const error = ref<unknown>(undefined)

    const scopeFilters = loopScopeFilters(parentExecutionId, taskId)

    let pendingCounts: Promise<void> | undefined

    function loadCounts({force = false}: {force?: boolean} = {}): Promise<void> {
        if (totalCount.value !== undefined && !force) return Promise.resolve()
        pendingCounts ??= fetchCounts().finally(() => { pendingCounts = undefined })
        return pendingCounts
    }

    async function fetchCounts(): Promise<void> {
        try {
            const [all, failed] = await Promise.all([
                executionsStore.findExecutions({...scopeFilters, page: 1, size: 1, commit: false, onlyTotal: true}),
                executionsStore.findExecutions({...scopeFilters, "filters[state][IN]": ["FAILED"], page: 1, size: 1, commit: false, onlyTotal: true}),
            ])
            totalCount.value = all ?? 0
            failedCount.value = failed ?? 0
        } catch {
            totalCount.value = undefined
            failedCount.value = undefined
        }
    }

    async function fetchAt(position: number): Promise<LoopIterationRow | undefined> {
        if (position < 0) return undefined
        const response = await executionsStore.findExecutions({
            ...scopeFilters,
            page: position + 1,
            size: 1,
            sort: "loopRunIndex:asc",
            commit: false,
        })
        const row = response.results?.[0]
        return row ? toLoopIterationRow(row as unknown as Record<string, unknown>) : undefined
    }

    let latestFetch = 0
    let pendingFetch: Promise<void> = Promise.resolve()

    function fetchPage(targetPage: number): Promise<void> {
        pendingFetch = runFetch(targetPage)
        return pendingFetch
    }

    async function runFetch(targetPage: number): Promise<void> {
        const fetch = ++latestFetch
        loading.value = true
        error.value = undefined
        try {
            const filters: Parameters<typeof executionsStore.findExecutions>[0] = {
                ...scopeFilters,
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
            const rows = (response.results ?? []).map(toLoopIterationRow)

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
        if (loaded.value) return Promise.resolve()
        if (loading.value) return pendingFetch
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
        if (value === failedOnly.value) return Promise.resolve()
        failedOnly.value = value
        if (!loaded.value && !loading.value) return Promise.resolve()
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
        totalCount,
        failedCount,
        loadCounts,
        fetchAt,
        ensureLoaded,
        loadMore,
        retry,
        setFailedOnly,
    }
}
