import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

interface LogStoreState {
    logs: any[] | undefined;
    total: number;
    level: string;
}

export const useLogsStore = defineStore("logs", {
    state: (): LogStoreState => ({
        logs: undefined,
        total: 0,
        level: "INFO",
    }),
    actions: {
        findLogs(options: any) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/logs/search`, {params: options}).then(response => {
                this.logs = response.data.results
                this.total = response.data.total
            })
        },
        deleteLogs(log: { namespace: string, flowId: string, triggerId?: string }) {
            const URL = `${apiUrl(this.vuexStore)}/logs/${log.namespace}/${log.flowId}${log.triggerId ? `?triggerId=${log.triggerId}` : ""}`;
            return this.$http.delete(URL).then(() => (this.logs = undefined))
        }
    }
})
