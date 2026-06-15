import {defineStore} from "pinia"
import {apiUrl} from "override/utils/route"
import {ref} from "vue"
import {useClient} from "@kestra-io/kestra-sdk"
import * as Utils from "../utils/utils"
import {LevelKey, formatLogsAsText, logsDownloadFilename} from "../utils/logs"

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
    const level = ref<LevelKey>("INFO")

    const axios = useClient()


    function findLogs(options: any) {
        return axios.get(`${apiUrl()}/logs/search`, {params: options}).then(response => {
            logs.value = response.data.results
            total.value = response.data.total
        })
    }

    function deleteLogs(log: { namespace: string, flowId: string, triggerId?: string }) {
        const URL = `${apiUrl()}/logs/${log.namespace}/${log.flowId}${log.triggerId ? `?triggerId=${log.triggerId}` : ""}`
        return axios.delete(URL).then(() => (logs.value = undefined))
    }

    function downloadLogs(options: any) {
        return axios
            .get(`${apiUrl()}/logs/search`, {params: {...options, page: 1, size: options.size ?? 10000}})
            .then(response => {
                const results = (response.data.results ?? []) as Log[]
                const text = formatLogsAsText(results.slice().reverse())
                Utils.downloadUrl(
                    window.URL.createObjectURL(new Blob([text], {type: "text/plain"})),
                    logsDownloadFilename(new Date()),
                )
            })
    }

    const LEVELS_ASC: LevelKey[] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]

    async function levelCounts(baseParams: any): Promise<Record<string, number>> {
        const cumulative = await Promise.all(LEVELS_ASC.map((logLevel) => {
            const params: Record<string, any> = {...baseParams, page: 1, size: 1}
            Object.keys(params)
                .filter((key) => key.startsWith("filters[level]"))
                .forEach((key) => delete params[key])
            params["filters[level][GREATER_THAN_OR_EQUAL_TO]"] = logLevel
            return axios
                .get(`${apiUrl()}/logs/search`, {params})
                .then((response) => (response.data.total ?? 0) as number)
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
        level,
        findLogs,
        deleteLogs,
        downloadLogs,
        levelCounts,
    }
})
