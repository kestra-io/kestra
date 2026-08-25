import {defineStore} from "pinia"
import {ref, computed} from "vue"
import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import type {QueryFilter} from "@kestra-io/kestra-sdk"
import {routeQueryToQueryFilters} from "../utils/queryFilters"
import * as Utils from "../utils/utils"
import {LevelKey, formatLogsAsText, logsDownloadFilename} from "../utils/logs"

/** Splits a flat `{page, size, sort, "filters[field][OP]": value, ...}` options object
 * (as built by callers' `loadQuery()` route.query merges) into the SDK's declared
 * page/size/sort params plus a proper QueryFilter[] array. */
function toSearchParams(options: Record<string, any>, cursor?: string) {
    const {page, size, sort, ...filterKeys} = options
    return {
        page,
        size,
        sort: sort ? [sort] : undefined,
        cursor,
        filters: routeQueryToQueryFilters(filterKeys),
    }
}

export interface Log{
    level: LevelKey;
    namespace: string;
    flowId: string;
    executionId: string;
    triggerId?: string;
    taskId?: string;
    thread: string;
    taskRunId?: string;
    index: number;
    attemptNumber: number;
    executionKind: "flow" | "playground";
    timestamp: string;
    message: string;
}

export const useLogsStore = defineStore("logs", () => {
    const logs = ref<Log[]>()
    const total = ref(0)
    const paginationType = ref<"OFFSET" | "CURSOR">("OFFSET")
    const nextCursor = ref<string | undefined>(undefined)

    const isCursorMode = computed(() => paginationType.value === "CURSOR")
    const hasNextCursor = computed(() => Boolean(nextCursor.value))

    // Cursor pagination is forward-only on the backend, but the client remembers every cursor it
    // used, so it can walk back. `cursorStack` holds the cursors that fetched the pages *behind* the
    // current one (index 0 is `undefined`, the first page); `currentCursor` fetched the current page.
    const cursorStack = ref<(string | undefined)[]>([])
    let currentCursor: string | undefined = undefined
    const hasPreviousPage = computed(() => cursorStack.value.length > 0)

    let latestSearchId = 0

    function applyResponse(response: Awaited<ReturnType<typeof LogsAPI.searchLogs>>) {
        logs.value = response.results as unknown as Log[]
        total.value = response.total ?? 0
        paginationType.value = response.type ?? "OFFSET"
        nextCursor.value = response.nextCursor
    }

    /** Fresh load — resets the cursor back-stack. Used for the first page, filter changes, refresh
     * and mode transitions. Next/Previous navigation goes through the dedicated actions below. */
    function findLogs(options: Record<string, any>, cursor?: string) {
        const searchId = ++latestSearchId
        return LogsAPI.searchLogs(toSearchParams(options, cursor)).then(response => {
            const isSuperseded = searchId !== latestSearchId
            if (isSuperseded) return

            cursorStack.value = []
            currentCursor = cursor
            applyResponse(response)
        })
    }

    /** Advance one page using the current `nextCursor`. The backend leaves `nextCursor` null only on
     * an empty page, so the last page *with logs* still advertises a cursor; when that Next comes back
     * empty we keep the current rows and just drop the Next control instead of blanking the view. */
    function loadNextPage(options: Record<string, any>) {
        const cursor = nextCursor.value
        if (!cursor) return Promise.resolve()
        const searchId = ++latestSearchId
        return LogsAPI.searchLogs(toSearchParams(options, cursor)).then(response => {
            const isSuperseded = searchId !== latestSearchId
            if (isSuperseded) return

            const results = (response.results ?? []) as unknown as Log[]
            if (results.length === 0) {
                nextCursor.value = undefined
                return
            }
            cursorStack.value = [...cursorStack.value, currentCursor]
            currentCursor = cursor
            applyResponse(response)
        })
    }

    /** Go back one page by re-fetching the previous page with the cursor that originally loaded it. */
    function loadPreviousPage(options: Record<string, any>) {
        if (cursorStack.value.length === 0) return Promise.resolve()
        const stack = [...cursorStack.value]
        const previousCursor = stack.pop()
        const searchId = ++latestSearchId
        return LogsAPI.searchLogs(toSearchParams(options, previousCursor)).then(response => {
            const isSuperseded = searchId !== latestSearchId
            if (isSuperseded) return

            cursorStack.value = stack
            currentCursor = previousCursor
            applyResponse(response)
        })
    }

    function deleteLogs(log: { namespace: string, flowId: string, triggerId?: string }) {
        return LogsAPI.deleteLogsFromFlow(log as Parameters<typeof LogsAPI.deleteLogsFromFlow>[0])
            .then(() => (logs.value = undefined))
    }

    function downloadLogs(options: Record<string, any>) {
        const params = toSearchParams({...options, page: 1, size: options.size ?? 1000})
        return LogsAPI.searchLogs(params)
            .then(response => {
                const results = (response.results ?? []) as unknown as Log[]
                const text = formatLogsAsText(results.slice().reverse())
                Utils.downloadUrl(
                    window.URL.createObjectURL(new Blob([text], {type: "text/plain"})),
                    logsDownloadFilename(new Date()),
                )
            })
    }

    const LEVELS_ASC: LevelKey[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]

    async function levelCounts(baseParams: Record<string, any>): Promise<Record<string, number>> {
        const baseFilters = routeQueryToQueryFilters(baseParams)
            .filter((f) => f.field !== "level")

        const cumulative = await Promise.all(LEVELS_ASC.map((logLevel) => {
            const filters: QueryFilter[] = [...baseFilters, {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: logLevel}]
            return LogsAPI.searchLogs({page: 1, size: 1, filters})
                .then((response) => (response.total ?? 0) as number)
                .catch(() => 0)
        }))

        const counts: Record<string, number> = {}
        LEVELS_ASC.forEach((logLevel, i) => {
            counts[logLevel] = Math.max(0, (cumulative[i] ?? 0) - (cumulative[i + 1] ?? 0))
        })
        return counts
    }

    return {
        logs,
        total,
        paginationType,
        nextCursor,
        isCursorMode,
        hasNextCursor,
        hasPreviousPage,
        findLogs,
        loadNextPage,
        loadPreviousPage,
        deleteLogs,
        downloadLogs,
        levelCounts,
    }
})
