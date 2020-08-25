import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        dailyGroupByFlow: undefined,
        daily: undefined,
    },
    actions: {
        dailyGroupByFlow({ commit }, payload) {
            return Vue.axios.post(`/api/v1/stats/executions/daily/group-by-flow`, payload).then(response => {
                commit('setDailyGroupByFlow', response.data)

                return response.data;
            })
        },
        daily({ commit }, payload) {
            return Vue.axios.post(`/api/v1/stats/executions/daily`, payload).then(response => {
                commit('setDaily', response.data)

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
        }
    },
    getters: {}
}
