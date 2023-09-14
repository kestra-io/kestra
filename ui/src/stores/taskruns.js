import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        taskruns: undefined,
        total: 0,
        maxTaskRunSetting: 100
    },
    actions: {
        findTaskRuns({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/taskruns/search`, {params: options}).then(response => {
                commit("setTaskruns", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
        maxTaskRunSetting({commit}) {
            return this.$http.get(`${apiUrl(this)}/taskruns/maxTaskRunSetting`).then(response => {
                commit("setMaxTaskRunSetting", response.data)
            })
        }
    },
    mutations: {
        setTaskruns(state, taskruns) {
            state.taskruns = taskruns
        },
        setTotal(state, total) {
            state.total = total
        },
        setMaxTaskRunSetting(state,maxTaskRunSetting){
            state.maxTaskRunSetting = maxTaskRunSetting
        }
    },
    getters: {}
}
