import Vue from "vue"
export default {
    namespaced: true,
    state: {
        taskruns: undefined,
        total: 0,
        maxTaskRunSetting: 100
    },
    actions: {
        findTaskRuns({commit}, options) {
            return Vue.axios.get("/api/v1/taskruns/search", {params: options}).then(response => {
                commit("setTaskruns", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
        maxTaskRunSetting({commit}) {
            return Vue.axios.get("/api/v1/taskruns/maxTaskRunSetting").then(response => {
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
