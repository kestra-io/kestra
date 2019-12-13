import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        executions: undefined,
        execution: undefined,
        task: undefined,
        total: 0
    },

    actions: {
        loadExecutions({ commit }, options) {
            return Vue.axios.get(`/api/v1/executions`, { params: options }).then(response => {
                commit('setExecutions', response.data.results)
                commit('setTotal', response.data.total)
            })
        },
        loadExecution({ commit }, options) {
            return Vue.axios.get(`/api/v1/executions/${options.id}`).then(response => {
                commit('setExecution', response.data)
            })
        },
        findExecutions({ commit }, options) {
            return Vue.axios.get(`/api/v1/executions/search`, {
                params: { q: '*' }
            }).then(response => {
                commit('setExecutions', response.data.results)
                commit('setTotal', response.data.total)
            })
        },
        triggerExecution({ commit }, options) {
            return Vue.axios.post(`/api/v1/flows/${options.namespace}/${options.id}/trigger`, options.formData, {
                headers: {
                    'content-type': 'multipart/form-data'
                }
            })
        },
        createFlow({ commit }, options) {
            return Vue.axios.post('/api/v1/executions', options.execution).then(response => {
                commit('setFlow', response.data.flow)
            })
        },
        followExecution(_, options) {
            return Vue.SSE(`${process.env.VUE_APP_API_URL}/api/v1/flows/${options.namespace}/${options.flowId}/executions/${options.id}/follow`, { format: 'json' })
        }
    },
    mutations: {
        setExecutions(state, executions) {
            state.executions = executions
        },
        setExecution(state, execution) {
            state.execution = execution
        },
        setTask(state, task) {
            state.task = task
        },
        setTotal(state, total) {
            state.total = total
        }
    },
    getters: {}
}