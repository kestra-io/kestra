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

    let latestSearchId = 0

    function findLogs(options: Record<string, any>, cursor?: string) {
        const searchId = ++latestSearchId
        return LogsAPI.searchLogs(toSearchParams(options, cursor)).then(response => {
            const isSuperseded = searchId !== latestSearchId
            if (isSuperseded) return

            logs.value = response.results as unknown as Log[]
            total.value = response.total ?? 0
            paginationType.value = response.type ?? "OFFSET"
            nextCursor.value = response.nextCursor
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
        findLogs,
        deleteLogs,
        downloadLogs,
        levelCounts,
    }
})
