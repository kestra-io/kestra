import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

type StatsPayload = Record<string, any>;
type DailyStats = Record<string, any>;
type TaskRunStats = Record<string, any>;
type LastExecutionStats = Record<string, any>;

interface State {
    dailyGroupByFlowData: DailyStats | undefined;
    dailyData: DailyStats | undefined;
    taskRunDailyData: TaskRunStats | undefined;
    lastExecutionsData: LastExecutionStats | undefined;
}

export const useStatStore = defineStore("stat", {
    state: (): State => ({
        dailyGroupByFlowData: undefined,
        dailyData: undefined,
        taskRunDailyData: undefined,
        lastExecutionsData: undefined
    }),

    actions: {
        async dailyGroupByFlow(payload: StatsPayload) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/stats/executions/daily/group-by-flow`, payload);
            this.dailyGroupByFlowData = response.data;
            return response.data;
        },

        async daily(payload: StatsPayload) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/stats/executions/daily`, payload);
            this.dailyData = response.data;
            return response.data;
        },

        async taskRunDaily(payload: StatsPayload) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/stats/taskruns/daily`, payload);
            this.taskRunDailyData = response.data;
            return response.data;
        },

        async lastExecutions(payload: StatsPayload) {
            const response = await this.$http.post(`${apiUrl(this.vuexStore)}/stats/executions/latest/group-by-flow`, payload);
            this.lastExecutionsData = response.data;
            return response.data;
        }
    }
});