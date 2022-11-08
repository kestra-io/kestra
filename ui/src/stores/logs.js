export default {
    namespaced: true,
    state: {
        logs: undefined,
        total: 0,
        level: "INFO"
    },
    actions: {
        findLogs({commit}, options) {
            return this.$http.get("/api/v1/logs/search", {params: options}).then(response => {
                commit("setLogs", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
    },
    mutations: {
        setLogs(state, logs) {
            state.logs = logs
        },
        setTotal(state, total) {
            state.total = total
        },
        setLevel(state, level) {
            state.level = level
        },
    },
}
