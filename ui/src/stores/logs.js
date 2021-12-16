import Vue from "vue"
export default {
    namespaced: true,
    state: {
        logs: undefined,
        total: 0,
        level: "INFO",
        fullscreen: false
    },
    actions: {
        findLogs({commit}, options) {
            return Vue.axios.get("/api/v1/logs/search", {params: options}).then(response => {
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
        setFullscreen(state, value) {
            state.fullscreen = value
        }
    },
}
