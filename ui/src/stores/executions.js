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
            return Vue.axios.get(`/api/v1/executions/search`, {params: options}).then(response => {
                commit('setExecutions', response.data.results)
                commit('setTotal', response.data.total)
            })
        },
        triggerExecution(_, options) {
            return Vue.axios.post(`/api/v1/executions/trigger/${options.namespace}/${options.id}`, options.formData, {
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
            return Vue.SSE(`${Vue.axios.defaults.baseURL}/api/v1/executions/${options.id}/follow`, { format: 'json' })
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