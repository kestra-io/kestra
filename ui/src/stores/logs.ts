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

/** One request per page; the export walks every page instead of keeping only the first. */
const DOWNLOAD_PAGE_SIZE = 1000

/** Safety ceiling: the file is assembled in memory, so an unbounded filter must not take the
 *  tab down with it. The caller is told when an export stops here. */
const DOWNLOAD_MAX_LINES = 50000

/**
 * Why an export ended.
 *
 * - `complete`  — everything the filters match was written.
 * - `truncated` — the backend's total says lines are missing, so the count is known.
 * - `capped`    — {@link DOWNLOAD_MAX_LINES} stopped it and no total is available to quantify it.
 * - `failed`    — a page request was refused; whatever had been collected is kept.
 */
export type LogsDownloadOutcome = "complete" | "truncated" | "capped" | "failed";

export interface LogsDownloadResult {
    /** Lines actually written to the file; 0 means no file was produced. */
    downloaded: number;
    /** Lines the filters match. Undefined under cursor pagination, which reports no total. */
    total?: number;
    outcome: LogsDownloadOutcome;
}

export interface Log{
    id?: string;
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

    /** Pages through the whole matching result set, so an export is no longer silently cut to
     *  the first page. `truncated` lets the caller warn when the safety ceiling kicked in. */
    async function downloadLogs(options: Record<string, any>): Promise<LogsDownloadResult> {
        const size = options.size ?? DOWNLOAD_PAGE_SIZE
        const collected: Log[] = []
        let reportedTotal: number | undefined = undefined
        let cappedOut = false
        let page = 1
        let cursor: string | undefined = undefined

        let failed = false

        for (;;) {
            let response: Awaited<ReturnType<typeof LogsAPI.searchLogs>>
            try {
                // This failure is reported by the caller, so opt out of the SDK's global error
                // toast: otherwise a 500 raises a raw internal-error message alongside it.
                response = await LogsAPI.searchLogs(
                    toSearchParams({...options, page, size}, cursor),
                    {showMessageOnError: false} as Parameters<typeof LogsAPI.searchLogs>[1],
                )
            } catch (error) {
                // Deep offset paging can be refused outright rather than returning a short page:
                // Elasticsearch caps `from + size` at `index.max_result_window` (10 000 by
                // default), so page 11 fails. Keep the pages already collected and say the export
                // is incomplete, instead of discarding all of them.
                console.error("Log export stopped early", error)
                failed = true
                break
            }

            const results = (response.results ?? []) as unknown as Log[]
            reportedTotal = response.total ?? reportedTotal
            collected.push(...results)

            if (collected.length >= DOWNLOAD_MAX_LINES) {
                cappedOut = true
                break
            }
            if (response.type === "CURSOR") {
                // Only an empty page ends a cursor walk: a cursor page may come back short
                // without being the last one, and a non-empty one always carries a cursor.
                if (results.length === 0 || !response.nextCursor) break
                cursor = response.nextCursor
            } else {
                if (results.length < size) break
                page += 1
            }
        }

        const lines = collected.slice(0, DOWNLOAD_MAX_LINES)
        // Nothing collected means no file: an empty download is the unusable artefact this
        // whole change exists to stop producing.
        if (lines.length > 0) {
            const text = formatLogsAsText(lines.slice().reverse())
            Utils.downloadUrl(
                window.URL.createObjectURL(new Blob([text], {type: "text/plain"})),
                logsDownloadFilename(new Date()),
            )
        }

        const matchedTotal = reportedTotal === undefined ? undefined : Math.max(reportedTotal, collected.length)

        // A known total is authoritative: deriving truncation from `cappedOut` alone reported a
        // complete 50 000-line export as truncated with 0 lines skipped. `cappedOut` only has to
        // stand on its own under cursor pagination, which reports no total at all.
        const outcome: LogsDownloadOutcome = failed
            ? "failed"
            : matchedTotal !== undefined
                ? (lines.length < matchedTotal ? "truncated" : "complete")
                : (cappedOut ? "capped" : "complete")

        return {downloaded: lines.length, total: matchedTotal, outcome}
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

    function bulkDeleteLogs(ids: string[]) {
        return axios.delete(`${apiUrl()}/logs/by-ids`, {data: ids})
    }

    function queryDeleteLogs(filters: Record<string, any>) {
        return axios.delete(`${apiUrl()}/logs/by-query`, {params: filters})
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
        bulkDeleteLogs,
        queryDeleteLogs,
        downloadLogs,
        levelCounts,
    }
})
