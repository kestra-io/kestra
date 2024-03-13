import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        executions: undefined,
        execution: undefined,
        taskRun: undefined,
        total: 0,
        logs: {
            total: 0,
            results: []
        },
        metrics: [],
        metricsTotal: 0,
        filePreview: undefined,
        subflowsExecutions: {}
    },
    actions: {
        loadExecutions({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/executions`, {params: options}).then(response => {
                commit("setExecutions", response.data.results)
                commit("setTotal", response.data.total)
            })
        },
        restartExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/restart`,
                null,
                {
                    params: {
                        revision: options.revision
                    }
                })
        },
        bulkRestartExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/restart/by-ids`,
                options.executionsId
            )
        },
        queryRestartExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/restart/by-query`,
                {},
                {params: options}
            )
        },
        bulkResumeExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/resume/by-ids`,
                options.executionsId
            )
        },
        queryResumeExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/resume/by-query`,
                {},
                {params: options}
            )
        },
        bulkReplayExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/replay/by-ids`,
                options.executionsId
            )
        },
        queryReplayExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/replay/by-query`,
                {},
                {params: options}
            )
        },
        replayExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/replay`,
                null,
                {
                    params: {
                        taskRunId: options.taskRunId,
                        revision: options.revision
                    }
                })
        },
        changeStatus(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/state`,
                {
                    taskRunId: options.taskRunId,
                    state: options.state,
                })
        },
        kill(_, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/${options.id}/kill?isOnKillCascade=${options.isOnKillCascade}`);
        },
        bulkKill(_, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/kill/by-ids`, {data: options.executionsId});
        },
        queryKill(_, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/kill/by-query`, {params: options});
        },
        resume(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/resume`);
        },
        loadExecution({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/executions/${options.id}`).then(response => {
                commit("setExecution", response.data)

                return response.data;
            })
        },
        findExecutions({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/executions/search`, {params: options}).then(response => {
                if (options.commit !== false) {
                    commit("setExecutions", response.data.results)
                    commit("setTotal", response.data.total)
                }

                return response.data
            })
        },
        triggerExecution(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.namespace}/${options.id}`, options.formData, {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                },
                params: {
                    labels: options.labels ?? []
                }
            })
        },
        deleteExecution({commit}, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/${options.id}`).then(() => {
                commit("setExecution", null)
            })
        },
        bulkDeleteExecution({_commit}, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/by-ids`, {data: options.executionsId})
        },
        queryDeleteExecution({_commit}, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/by-query`, {params: options})
        },
        followExecution(_, options) {
            return new EventSource(`${apiUrl(this)}/executions/${options.id}/follow`);
        },
        followLogs(_, options) {
            return new EventSource(`${apiUrl(this)}/logs/${options.id}/follow`);
        },
        loadLogs({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data
                }
                if(options.params.page !== 1) {
                    commit("appendLogs", response.data)
                } else {
                    commit("setLogs", response.data)
                }

                return response.data
            });
        },
        loadMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data
                }
                commit("setMetrics", response.data.results)
                commit("setMetricsTotal", response.data.total)

                return response.data
            });
        },
        downloadLogs(_, options) {
            return this.$http.get(`${apiUrl(this)}/logs/${options.executionId}/download`, {
                params: options.params
            }).then(response => {
                return response.data
            })
        },
        deleteLogs(_, options) {
            return this.$http.delete(`${apiUrl(this)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                return response.data
            })
        },
        filePreview({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/executions/${options.executionId}/file/preview`, {
                params: options
            }).then(response => {
                commit("setFilePreview", response.data)
            })
        },
        setLabels(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/labels`,
                options.labels,
                {
                    headers: {
                        "Content-Type": "application/json"
                    }
                })
        },
        querySetLabels({_commit}, options) {
            return this.$http.post(`${apiUrl(this)}/executions/labels/by-query`, options.data, {
                params: options.params})
        },
        bulkSetLabels({_commit}, options) {
            return this.$http.post(`${apiUrl(this)}/executions/labels/by-ids`,  options)
        }
    },
    mutations: {
        setExecutions(state, executions) {
            state.executions = executions
        },
        setExecution(state, execution) {
            state.execution = execution
        },
        addSubflowExecution(state, params) {
            state.subflowsExecutions[params.subflow] = params.execution
        },
        removeSubflowExecution(state, subflow) {
            delete state.subflowsExecutions[subflow]
        },
        setSubflowExecutions(state, subflowsExecution) {
            state.subflowsExecution = subflowsExecution
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
        resetLogs(state) {
            state.logs = {results:[], total:0}
        },
        appendLogs(state, logs) {
            state.logs.results = state.logs.results.concat(logs.results)
        },
        appendFollowedLogs(state, logs) {
            state.logs.results.push(logs)
            state.logs.total = state.logs.results.length
        },
        setMetrics(state, metrics) {
            state.metrics = metrics
        },
        setMetricsTotal(state, metrics) {
            state.metricsTotal = metrics
        },
        setFilePreview(state, filePreview) {
            state.filePreview = filePreview
        }
    },
    getters: {
        execution(state) {
            if (state.execution) {
                return state.execution;
            }
        },
        subflowsExecutions(state) {
            if (state.subflowsExecutions) {
                return state.subflowsExecutions;
            }
        }
    }
}
