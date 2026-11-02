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

    function listFilters(): Parameters<typeof executionsStore.findExecutions>[0] {
        return failedOnly.value
            ? {...scopeFilters, "filters[state][IN]": ["FAILED"], sort: "loopRunIndex:asc", commit: false}
            : {...scopeFilters, sort: "loopRunIndex:asc", commit: false}
    }

    async function fetchAt(position: number): Promise<LoopIterationRow | undefined> {
        if (position < 0) return undefined
        const response = await executionsStore.findExecutions({...listFilters(), page: position + 1, size: 1})
        const row = response.results?.[0]
        return row ? toLoopIterationRow(row as unknown as Record<string, unknown>) : undefined
    }

    async function fetchNeighbour(index: number, delta: -1 | 1, currentPosition?: number): Promise<LoopIterationRow | undefined> {
        const isNeighbour = (row: LoopIterationRow | undefined) => row !== undefined && (delta === 1 ? row.index > index : row.index < index)
        if (delta === -1 && index === 0) return undefined

        const probes = new Map<number, Promise<LoopIterationRow | undefined>>()
        const probe = (position: number) => {
            if (!probes.has(position)) probes.set(position, fetchAt(position))
            return probes.get(position)!
        }

        if (currentPosition !== undefined) {
            const row = await probe(currentPosition + delta)
            return isNeighbour(row) ? row : undefined
        }
        if (!failedOnly.value) {
            const guess = await probe(index + delta)
            if (guess?.index === index + delta) return guess
        }

        await loadCounts()
        const count = failedOnly.value ? failedCount.value : totalCount.value
        if (count === undefined) return undefined

        const isPast = (row: LoopIterationRow) => delta === 1 ? row.index > index : row.index >= index
        let low = 0
        let high = count
        while (low < high) {
            const middle = Math.floor((low + high) / 2)
            const row = await probe(middle)
            if (!row || isPast(row)) high = middle
            else low = middle + 1
        }
        const target = delta === 1 ? low : low - 1
        if (target < 0 || target >= count) return undefined
        const row = await probe(target)
        return isNeighbour(row) ? row : undefined
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
            const response = await executionsStore.findExecutions({...listFilters(), page: targetPage, size: LOOP_ITERATIONS_PAGE_SIZE})
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

    function reload(): Promise<void> {
        const counts = totalCount.value !== undefined ? loadCounts({force: true}) : Promise.resolve()
        const rows = loaded.value || loading.value ? fetchPage(1) : Promise.resolve()
        return Promise.all([counts, rows]).then(() => undefined)
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
        fetchNeighbour,
        ensureLoaded,
        reload,
        loadMore,
        retry,
        setFailedOnly,
    }
}
