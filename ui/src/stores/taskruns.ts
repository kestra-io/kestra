import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

interface TaskRunsStoreState {
    taskruns: any[] | undefined;
    total: number;
}

export const useTaskRunsStore = defineStore("taskruns", {
    state: (): TaskRunsStoreState => ({
        taskruns: undefined,
        total: 0,
    }),
    actions: {
        findTaskRuns(options: any) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/taskruns/search`, {params: options}).then(response => {
                this.taskruns = response.data.results;
                this.total = response.data.total;
            });
        },
    }
});
