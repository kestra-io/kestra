import Vue from 'vue'
import Execution from '../../execution-sample.json'
export default {
    namespaced: true,
    state: {
        executions: undefined,
        execution: undefined,
        total: 0
    },

    actions: {
        loadExecutions({ commit }, options) {
            // return Vue.axios.get(`/api/v1/executions/${options.namespace}`, { params: { size: options.perPage, page: options.page } }).then(response => {
            //     commit('setExecutions', response.data.results)
            //     commit('setTotal', response.data.total)
            // })
        },
        loadExecution({ commit }, options) {
            // return Vue.axios.get(`/api/v1/executions/${options.namespace}/${options.id}`).then(response => {
            //     commit('setExecution', response.data)
            // })
            console.log(Execution)
            commit('setExecution', Execution)

        },
        createFlow({ commit }, options) {
            return Vue.axios.post('/api/v1/executions', options.execution).then(response => {
                commit('setFlow', response.data.flow)
            })
        }
    },
    mutations: {
        setExecutions(state, executions) {
            state.executions = executions
        },
        setExecution(state, execution) {
            state.execution = execution
        },
        setTotal(state, total) {
            state.total = total
        }
    },
    getters: {}
}