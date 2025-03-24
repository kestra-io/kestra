import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        dailyGroupByFlow: undefined,
        daily: undefined,
        logDaily: undefined,
        taskRunDaily: undefined,
        lastExecutions: undefined
    },
    actions: {
        dailyGroupByFlow({commit}, payload) {
            return this.$http.post(`${apiUrl(this)}/stats/executions/daily/group-by-flow`, payload).then(response => {
                commit("setDailyGroupByFlow", response.data)

                return response.data;
            })
        },
        dailyGroupByNamespace({_}, payload) {
            return this.$http.post(`${apiUrl(this)}/stats/executions/daily/group-by-namespace`, payload).then(response => response.data)
        },
        daily({commit}, payload) {
            return this.$http.post(`${apiUrl(this)}/stats/executions/daily`, payload).then(response => {
                commit("setDaily", response.data)

                return response.data;
            })
        },
        logDaily({commit}, payload) {
            return this.$http.post(`${apiUrl(this)}/stats/logs/daily`, payload).then(response => {
                commit("setLogDaily", response.data)

                return response.data;
            })
        },
        taskRunDaily({commit}, payload) {
            return this.$http.post(`${apiUrl(this)}/stats/taskruns/daily`, payload).then(response => {
                commit("setTaskRunDaily", response.data)

                return response.data;
            })
        },
        lastExecutions({commit}, payload) {
            return this.$http.post(`${apiUrl(this)}/stats/executions/latest/group-by-flow`, payload).then(response => {
                commit("setLastExecutions", response.data)

                return response.data;
            })
        }
    },
    mutations: {
        setDailyGroupByFlow(state, stats) {
            state.dailyGroupByFlow = stats
        },
        setDaily(state, stats) {
            state.daily = stats
        },
        setLogDaily(state, stats) {
            state.logDaily = stats
        },
        setTaskRunDaily(state, stats) {
            state.taskRunDaily = stats
        },
        setLastExecutions(state, stats) {
            state.lastExecutions = stats
        }
    },
    getters: {}
}
