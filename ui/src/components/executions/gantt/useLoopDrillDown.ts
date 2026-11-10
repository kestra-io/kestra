import {computed, ref, watch} from "vue"
import {useDebounceFn} from "@vueuse/core"
import {useExecutionsStore} from "../../../stores/executions"
import {
    LOOP_ITERATIONS_PAGE_SIZE,
    loopScopeFilters,
    toLoopIterationRow,
    type LoopIterationRow,
} from "../../../composables/useLoopIterations"
import type {LoopRef} from "./ganttTree"

export type DrillDownState = "all" | "failed" | "success"

const STATE_FILTERS: Record<DrillDownState, string[] | undefined> = {
    all: undefined,
    failed: ["FAILED"],
    success: ["SUCCESS"],
}

const SEARCH_DEBOUNCE_MS = 300

export function useLoopDrillDown(loop: LoopRef) {
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
    const counts = ref<Record<DrillDownState, number | undefined>>({all: undefined, failed: undefined, success: undefined})

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

    let latestFetch = 0
    async function load() {
        const fetch = ++latestFetch
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
            if (fetch !== latestFetch) return
            rows.value = (response.results ?? []).map(toLoopIterationRow)
            total.value = response.total ?? 0
        } catch (e) {
            if (fetch === latestFetch) error.value = e
        } finally {
            if (fetch === latestFetch) loading.value = false
        }
    }

    async function loadCounts() {
        const entries = await Promise.all((Object.keys(STATE_FILTERS) as DrillDownState[]).map(async (filterState) => {
            try {
                const count = await executionsStore.findExecutions({
                    ...filtersFor(filterState, ""),
                    page: 1,
                    size: 1,
                    commit: false,
                    onlyTotal: true,
                })
                return [filterState, count ?? 0] as const
            } catch {
                return [filterState, undefined] as const
            }
        }))
        counts.value = Object.fromEntries(entries) as Record<DrillDownState, number | undefined>
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

    load()
    loadCounts()

    return {state, search, page, rows, total, loading, error, counts, pageCount, firstShown, lastShown, load, goToPage}
}
