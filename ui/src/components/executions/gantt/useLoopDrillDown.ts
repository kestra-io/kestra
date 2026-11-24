import {computed, ref, watch} from "vue"
import {useDebounceFn} from "@vueuse/core"
import {useExecutionsStore} from "../../../stores/executions"
import {
    LOOP_ITERATIONS_PAGE_SIZE,
    loopScopeFilters,
    toLoopIterationRow,
    type LoopIterationRow,
} from "../../../composables/useLoopIterations"
import type {LoopHandle, LoopRef} from "./ganttTree"
import {latestRequest} from "../../../utils/latestRequest"

export type DrillDownState = "all" | "failed" | "success"

const STATE_FILTERS: Record<DrillDownState, string[] | undefined> = {
    all: undefined,
    failed: ["FAILED"],
    success: ["SUCCESS"],
}

const SEARCH_DEBOUNCE_MS = 300

export function useLoopDrillDown(loop: LoopRef, handle: Pick<LoopHandle, "totalCount" | "failedCount" | "loadCounts">) {
    const executionsStore = useExecutionsStore()
    const scope = loopScopeFilters(loop.parentExecutionId, loop.taskId)

    const state = ref<DrillDownState>("all")
    const search = ref("")
    const query = ref("")
    const page = ref(1)
    const rows = ref<LoopIterationRow[]>([])
    const total = ref(0)
    const loading = ref(false)
    const error = ref<unknown>(undefined)
    const successCount = ref<number | undefined>(undefined)
    const counts = computed<Record<DrillDownState, number | undefined>>(() => ({
        all: handle.totalCount.value,
        failed: handle.failedCount.value,
        success: successCount.value,
    }))

    const pageCount = computed(() => Math.max(1, Math.ceil(total.value / LOOP_ITERATIONS_PAGE_SIZE)))
    const firstShown = computed(() => total.value === 0 ? 0 : (page.value - 1) * LOOP_ITERATIONS_PAGE_SIZE + 1)
    const lastShown = computed(() => (page.value - 1) * LOOP_ITERATIONS_PAGE_SIZE + rows.value.length)

    function filtersFor(filterState: DrillDownState, value: string) {
        const filters: Record<string, unknown> = {...scope}
        const states = STATE_FILTERS[filterState]
        if (states) filters["filters[state][IN]"] = states
        if (value) filters["filters[loopRunValue][EQUALS]"] = value
        return filters
    }

    const startFetch = latestRequest()
    async function load() {
        const isLatest = startFetch()
        loading.value = true
        error.value = undefined
        try {
            const response = await executionsStore.findExecutions({
                ...filtersFor(state.value, query.value),
                page: page.value,
                size: LOOP_ITERATIONS_PAGE_SIZE,
                sort: "loopRunIndex:asc",
                commit: false,
            })
            if (!isLatest()) return
            rows.value = (response.results ?? []).map(toLoopIterationRow)
            total.value = response.total ?? 0
        } catch (e) {
            if (isLatest()) error.value = e
        } finally {
            if (isLatest()) loading.value = false
        }
    }

    async function loadSuccessCount() {
        try {
            const count = await executionsStore.findExecutions({
                ...filtersFor("success", ""),
                page: 1,
                size: 1,
                commit: false,
                onlyTotal: true,
            })
            successCount.value = count ?? 0
        } catch {
            successCount.value = undefined
        }
    }

    const applySearch = useDebounceFn(() => {
        query.value = search.value.trim()
    }, SEARCH_DEBOUNCE_MS)

    watch(search, applySearch)
    watch([state, query], () => {
        page.value = 1
        load()
    })

    function goToPage(target: number) {
        page.value = Math.min(Math.max(target, 1), pageCount.value)
        load()
    }

    function refresh() {
        load()
        handle.loadCounts({force: true})
        loadSuccessCount()
    }

    refresh()

    return {state, search, page, rows, total, loading, error, counts, pageCount, firstShown, lastShown, load, refresh, goToPage}
}
