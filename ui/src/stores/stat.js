import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        stats: undefined,
    },
    actions: {
        dailyGroupByFlow({ commit }, payload) {
            return Vue.axios.post(`/api/v1/stats/executions/daily/group-by-flow`, payload).then(response => {
                commit('setStats', response.data)

                return response.data;
            })
        },
    },
    mutations: {
        setStats(state, stats) {
            state.stats = stats
        }
    },
    getters: {}
}
