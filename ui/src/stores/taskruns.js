import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        taskruns: undefined,
        total: 0,
    },
    actions: {
        findTaskRuns({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/taskruns/search`, {params: options}).then(response => {
                commit("setTaskruns", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
    },
    mutations: {
        setTaskruns(state, taskruns) {
            state.taskruns = taskruns
        },
        setTotal(state, total) {
            state.total = total
        },
    },
    getters: {}
}
