export default {
    namespaced: true,
    state: {
        dailyGroupByFlow: undefined,
        daily: undefined,
        taskRunDaily: undefined
    },
    actions: {
        dailyGroupByFlow({commit}, payload) {
            return this.$http.post("/api/v1/stats/executions/daily/group-by-flow", payload).then(response => {
                commit("setDailyGroupByFlow", response.data)

                return response.data;
            })
        },
        daily({commit}, payload) {
            return this.$http.post("/api/v1/stats/executions/daily", payload).then(response => {
                commit("setDaily", response.data)

                return response.data;
            })
        },
        taskRunDaily({commit}, payload) {
            return this.$http.post("/api/v1/stats/taskruns/daily", payload).then(response => {
                commit("setTaskRunDaily", response.data)

                return response.data;
            })
        },
    },
    mutations: {
        setDailyGroupByFlow(state, stats) {
            state.dailyGroupByFlow = stats
        },
        setDaily(state, stats) {
            state.daily = stats
        },
        setTaskRunDaily(state, stats) {
            state.taskRunDaily = stats
        }
    },
    getters: {}
}
