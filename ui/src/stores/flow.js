import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        flows: undefined,
        flow: undefined,
        total: 0
    },

    actions: {
        loadFlows({ commit }, options) {
            return Vue.axios.get(`/api/v1/flows/${options.namespace}`, { params: { size: options.size, page: options.page } }).then(response => {
                commit('setFlows', response.data.results)
                commit('setTotal', response.data.total)
            })
        },
        findFlows({ commit }, options) {
            const sort = options.sort
            delete options.sort
            let sortQueryString = ''
            if (sort) {
                sortQueryString = `?sort=${sort.join('&sort=')}`
            }
            return Vue.axios.get(`/api/v1/flows/search${sortQueryString}`, {
                params: options
            }).then(response => {
                commit('setFlows', response.data.results)
                commit('setTotal', response.data.total)
            })
        },
        loadFlow({ commit }, options) {
            return Vue.axios.get(`/api/v1/flows/${options.namespace}/${options.id}`).then(response => {
                commit('setFlow', response.data)
            })
        },
        saveFlow({ commit }, options) {
            return Vue.axios.put(`/api/v1/flows/${options.flow.namespace}/${options.flow.id}`, options.flow).then(response => {
                commit('setFlow', response.data)
            })
        },
        createFlow({ commit }, options) {
            return Vue.axios.post('/api/v1/flows', options.flow).then(response => {
                commit('setFlow', response.data.flow)
            })
        }
    },
    mutations: {
        setFlows(state, flows) {
            state.flows = flows
        },
        setFlow(state, flow) {
            state.flow = flow
        },
        setTotal(state, total) {
            state.total = total
        }
    },
    getters: {}
}