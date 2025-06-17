import {defineStore} from "pinia";
import axios from "axios";
import {apiUrl} from "override/utils/route";
import {Store} from "vuex";

interface LogStoreState {
    logs: any[] | undefined;
    total: number;
    level: string;
    vuexStore: Store<any> | undefined;
}

export const useLogsStore = defineStore("logs", {
    state: (): LogStoreState => ({
        logs: undefined,
        total: 0,
        level: "INFO",
        vuexStore: undefined
    }),
    actions: {
        setVuexStore(store: Store<any>) {
            this.vuexStore = store;
        },
        findLogs(options: any) {
            return axios.get(`${apiUrl(this.vuexStore)}/logs/search`, {params: options}).then(response => {
                this.logs = response.data.results
                this.total = response.data.total
            })
        },
        deleteLogs(log: { namespace: string, flowId: string, triggerId?: string }) {
            const URL = `${apiUrl(this.vuexStore)}/logs/${log.namespace}/${log.flowId}${log.triggerId ? `?triggerId=${log.triggerId}` : ""}`;
            return axios.delete(URL).then(() => (this.logs = undefined))
        }
    }
})
