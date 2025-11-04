import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {ref} from "vue";
import {useAxios} from "../utils/axios";
import {LevelKey} from "../utils/logs";

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

    const axios = useAxios();


    function findLogs(options: any) {
        return axios.get(`${apiUrl()}/logs/search`, {params: options}).then(response => {
            logs.value = response.data.results
            total.value = response.data.total
        })
    }

    function deleteLogs(log: { namespace: string, flowId: string, triggerId?: string }) {
        const URL = `${apiUrl()}/logs/${log.namespace}/${log.flowId}${log.triggerId ? `?triggerId=${log.triggerId}` : ""}`;
        return axios.delete(URL).then(() => (logs.value = undefined))
    }

    return {
        logs,
        total,
        level,
        findLogs,
        deleteLogs,
    }
})
