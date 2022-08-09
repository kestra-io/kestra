import Vue from "vue"
export default {
    namespaced: true,
    state: {
        executions: undefined,
        execution: undefined,
        taskRun: undefined,
        task: undefined,
        total: 0,
        logs: []
    },
    actions: {
        loadExecutions({commit}, options) {
            return Vue.axios.get("/api/v1/executions", {params: options}).then(response => {
                commit("setExecutions", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
        restartExecution(_, options) {
            return Vue.axios.post(
                `/api/v1/executions/${options.executionId}/restart`,
                null,
                {
                    params: {
                        revision: options.revision
                    }
                })
        },
        replayExecution(_, options) {
            return Vue.axios.post(
                `/api/v1/executions/${options.executionId}/replay`,
                null,
                {
                    params: {
                        taskRunId: options.taskRunId,
                        revision: options.revision
                    }
                })
        },
        changeStatus(_, options) {
            return Vue.axios.post(
                `/api/v1/executions/${options.executionId}/state`,
                {
                    taskRunId: options.taskRunId,
                    state: options.state,
                })
        },
        kill(_, options) {
            return Vue.axios.delete(`/api/v1/executions/${options.id}/kill`);
        },
        loadExecution({commit}, options) {
            return Vue.axios.get(`/api/v1/executions/${options.id}`).then(response => {
                commit("setExecution", response.data)

                return response.data;
            })
        },
        findExecutions({commit}, options) {
            const sort = options.sort
            delete options.sort
            let sortQueryString = ""
            if (sort) {
                sortQueryString = `?sort=${sort}`
            }
            return Vue.axios.get(`/api/v1/executions/search${sortQueryString}`, {params: options}).then(response => {
                commit("setExecutions", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
        triggerExecution(_, options) {
            return Vue.axios.post(`/api/v1/executions/trigger/${options.namespace}/${options.id}`, options.formData, {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            })
        },
        deleteExecution({commit}, options) {
            return Vue.axios.delete(`/api/v1/executions/${options.id}`).then(() => {
                commit("setExecution", null)
            })
        },
        followExecution(_, options) {
            return new EventSource(`${Vue.axios.defaults.baseURL}api/v1/executions/${options.id}/follow`);
        },
        followLogs(_, options) {
            return new EventSource(`${Vue.axios.defaults.baseURL}api/v1/logs/${options.id}/follow`);
        },
        loadLogs({commit}, options) {
            return Vue.axios.get(`/api/v1/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                commit("setLogs", response.data)
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
        setTask(state, task) {
            state.task = task
        },
        setTaskRun(state, taskRun) {
            state.taskRun = taskRun
        },
        setTotal(state, total) {
            state.total = total
        },
        setLogs(state, logs) {
            state.logs = logs
        },
        appendLogs(state, logs) {
            state.logs.push(logs);
        }
    },
    getters: {}
}
