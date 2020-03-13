import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        executions: undefined,
        execution: undefined,
        task: undefined,
        total: 0,
        dataTree: undefined
    },
    actions: {
        loadExecutions({ commit }, options) {
            return Vue.axios.get(`/api/v1/executions`, { params: options }).then(response => {
                commit('setExecutions', response.data.results)
                commit('setTotal', response.data.total)
            })
        },
        restartExecution({ commit }, options) {
            return Vue.axios.post(`/api/v1/executions/${options.id}/restart?taskId=${options.taskId}`, {params: options},{
                headers: {
                    'content-type': 'multipart/form-data'
                }
            })
        },
        loadExecution({ commit }, options) {
            return Vue.axios.get(`/api/v1/executions/${options.id}`).then(response => {
                commit('setExecution', response.data)
            })
        },
        findExecutions({ commit }, options) {
            const sort = options.sort
            delete options.sort
            let sortQueryString = ''
            if (sort) {
                sortQueryString = `?sort=${sort}`
            }
            return Vue.axios.get(`/api/v1/executions/search${sortQueryString}`, {params: options}).then(response => {
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
            return Vue.SSE(`${Vue.axios.defaults.baseURL}api/v1/executions/${options.id}/follow`, { format: 'json' })
        },
        loadTree({ commit }, execution) {
            return Vue.axios.get(`/api/v1/executions/${execution.id}/tree`).then(response => {
                commit('setDataTree', response.data.tasks)
            })
        },
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
        },
        setDataTree(state, tree) {
            state.dataTree = tree
        }
    },
    getters: {}
}
