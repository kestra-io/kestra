import {defineStore} from "pinia"
import {ref} from "vue"
import * as LogsAPI from "@kestra-io/kestra-sdk/logs"
import {routeQueryToQueryFilters, type QueryFilter} from "@kestra-io/design-system"
import * as Utils from "../utils/utils"
import {LevelKey, formatLogsAsText, logsDownloadFilename} from "../utils/logs"

/** Splits a flat `{page, size, sort, "filters[field][OP]": value, ...}` options object
 * (as built by callers' `loadQuery()` route.query merges) into the SDK's declared
 * page/size/sort params plus a proper QueryFilter[] array. */
function toSearchParams(options: Record<string, any>) {
    const {page, size, sort, ...filterKeys} = options
    return {
        page,
        size,
        sort: sort ? [sort] : undefined,
        filters: routeQueryToQueryFilters(filterKeys),
    } as Parameters<typeof LogsAPI.searchLogs>[0]
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

    function findLogs(options: Record<string, any>) {
        return LogsAPI.searchLogs(toSearchParams(options)).then(response => {
            logs.value = response.results as unknown as Log[]
            total.value = response.total ?? 0
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
        const baseFilters = (routeQueryToQueryFilters(baseParams) as QueryFilter[])
            .filter((f) => f.field !== "level")

        const cumulative = await Promise.all(LEVELS_ASC.map((logLevel) => {
            const filters = [...baseFilters, {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: logLevel}]
            return LogsAPI.searchLogs({page: 1, size: 1, filters} as Parameters<typeof LogsAPI.searchLogs>[0])
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
        findLogs,
        deleteLogs,
        downloadLogs,
        levelCounts,
    }
})
