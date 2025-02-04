import axios from "axios";
import {apiUrl} from "override/utils/route";
import Utils from "../utils/utils.js"

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
        subflowsExecutions: {},
        flow: undefined,
        flowGraph: undefined,
        namespaces: [],
        flowsExecutable: []
    },
    actions: {
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
        bulkChangeExecutionStatus(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/change-status/by-ids`,
                options.executionsId,
                {
                    params: {
                        newStatus: options.newStatus
                    }
                }
            )
        },
        queryReplayExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/replay/by-query`,
                {},
                {params: options}
            )
        },
        queryChangeExecutionStatus(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/change-status/by-query`,
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
        changeExecutionStatus(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/change-status`,
                null,
                {
                    params: {
                        status: options.state
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
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/resume`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            });
        },
        validateResume(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/resume/validate`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            });
        },
        pause(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/pause`);
        },
        bulkPauseExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/pause/by-ids`,
                options.executionsId
            )
        },
        queryPauseExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/pause/by-query`,
                {},
                {params: options}
            )
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
        validateExecution(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.namespace}/${options.id}/validate`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                },
                params: {
                    labels: options.labels ?? [],
                    scheduleDate: options.scheduleDate
                }
            })
        },
        triggerExecution(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.namespace}/${options.id}`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                },
                params: {
                    labels: options.labels ?? [],
                    scheduleDate: options.scheduleDate
                }
            })
        },
        deleteExecution({commit}, options) {
            const {id, deleteLogs, deleteMetrics, deleteStorage} = options
            const qs = Object.entries({deleteLogs, deleteMetrics, deleteStorage}).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join("&");

            return this.$http.delete(`${apiUrl(this)}/executions/${id}?${qs}`).then(() => {
                commit("setExecution", null)
            })
        },
        bulkDeleteExecution({_commit}, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/by-ids`, {data: options.executionsId, params: {...options}})
        },
        queryDeleteExecution({_commit}, options) {
            return this.$http.delete(`${apiUrl(this)}/executions/by-query`, {params: options})
        },
        followExecution(_, options) {
            return new EventSource(`${apiUrl(this)}/executions/${options.id}/follow`, {withCredentials: true});
        },
        followLogs(_, options) {
            return new EventSource(`${apiUrl(this)}/logs/${options.id}/follow`, {withCredentials: true});
        },
        loadLogs({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data
                }
                commit("setLogs", response.data)

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
                let data = {...response.data}

                // WORKAROUND, related to https://github.com/kestra-io/plugin-aws/issues/456
                if(data.extension === "ion") {
                    const notObjects = data.content.some(e => typeof e !== "object");

                    if(notObjects) {
                        const content = data.content.length === 1 ? data.content[0] : data.content.join("\n");
                        data = {...data, type: "TEXT", content}
                    }
                }

                commit("setFilePreview", data);
                return data;
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
        },
        unqueue(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/unqueue`);
        },
        bulkUnqueueExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/unqueue/by-ids`,
                options.executionsId
            )
        },
        queryUnqueueExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/unqueue/by-query`,
                {},
                {params: options}
            )
        },
        forceRun(_, options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/force-run`);
        },
        bulkForceRunExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/force-run/by-ids`,
                options.executionsId
            )
        },
        queryForceRunExecution(_, options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/force-run/by-query`,
                {},
                {params: options}
            )
        },
        loadFlowForExecution({commit}, options) {
            const revision = options.revision ? `?revision=${options.revision}` : "";
            return this.$http.get(`${apiUrl(this)}/executions/flows/${options.namespace}/${options.flowId}${revision}`)
                .then(response => {
                    commit("setFlow", response.data)
                    return response.data;
                });
        },
        loadFlowForExecutionByExecutionId({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/executions/${options.id}/flow`)
                .then(response => {
                    commit("setFlow", response.data)
                    return response.data;
                });
        },
        loadGraph({commit}, options) {
            const params = options.params ? options.params : {};
            return axios.get(`${apiUrl(this)}/executions/${options.id}/graph`, {params, withCredentials: true, paramsSerializer: {indexes: null}})
                .then(response => {
                    commit("setFlowGraph", response.data)
                })
        },
        loadNamespaces({commit}) {
            return this.$http.get(`${apiUrl(this)}/executions/namespaces`)
                .then(response => {
                    commit("setNamespaces", response.data)
                })
        },
        loadFlowsExecutable({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/executions/namespaces/${options.namespace}/flows`)
                .then(response => {
                    commit("setFlowsExecutable", response.data)
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
        },
        setFlow(state, flow) {
            state.flow = flow
        },
        setFlowGraph(state, flowGraph) {
            state.flowGraph = flowGraph
        },
        setNamespaces(state, namespaces) {
            state.namespaces = namespaces
        },
        setFlowsExecutable(state, flowsExecutable) {
            state.flowsExecutable = flowsExecutable
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
