import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import Utils from "../utils/utils"

export default defineStore("executions", {
    state: () => ({
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
    }),
    actions: {
        restartExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/restart`,
                null,
                {
                    params: {
                        revision: options.revision
                    }
                })
        },
        bulkRestartExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/restart/by-ids`,
                options.executionsId
            )
        },
        queryRestartExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/restart/by-query`,
                {},
                {params: options}
            )
        },
        bulkResumeExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/resume/by-ids`,
                options.executionsId
            )
        },
        queryResumeExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/resume/by-query`,
                {},
                {params: options}
            )
        },
        bulkReplayExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/replay/by-ids`,
                options.executionsId,
                {params: options}
            )
        },
        bulkChangeExecutionStatus(options) {
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
        queryReplayExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/replay/by-query`,
                {},
                {params: options}
            )
        },
        queryChangeExecutionStatus(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/change-status/by-query`,
                {},
                {params: options}
            )
        },
        replayExecution(options) {
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
        changeExecutionStatus(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/change-status`,
                null,
                {
                    params: {
                        status: options.state
                    }
                })
        },
        changeStatus(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/state`,
                {
                    taskRunId: options.taskRunId,
                    state: options.state,
                })
        },
        kill(options) {
            return this.$http.delete(`${apiUrl(this)}/executions/${options.id}/kill?isOnKillCascade=${options.isOnKillCascade}`);
        },
        bulkKill(options) {
            return this.$http.delete(`${apiUrl(this)}/executions/kill/by-ids`, {data: options.executionsId});
        },
        queryKill(options) {
            return this.$http.delete(`${apiUrl(this)}/executions/kill/by-query`, {params: options});
        },
        resume(options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/resume`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            });
        },
        validateResume(options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/resume/validate`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            });
        },
        pause(options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/pause`);
        },
        bulkPauseExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/pause/by-ids`,
                options.executionsId
            )
        },
        queryPauseExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/pause/by-query`,
                {},
                {params: options}
            )
        },
        loadExecution(options) {
            return this.$http.get(`${apiUrl(this)}/executions/${options.id}`).then(response => {
                this.execution = response.data

                return response.data;
            })
        },
        findExecutions(options) {
            return this.$http.get(`${apiUrl(this)}/executions/search`, {params: options}).then(response => {
                if (options.commit !== false) {
                    this.executions = response.data.results
                    this.total = response.data.total
                }

                return response.data
            })
        },
        validateExecution(options) {
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
        triggerExecution(options) {
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
        deleteExecution(options) {
            const {id, deleteLogs, deleteMetrics, deleteStorage} = options
            const qs = Object.entries({deleteLogs, deleteMetrics, deleteStorage}).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join("&");

            return this.$http.delete(`${apiUrl(this)}/executions/${id}?${qs}`).then(() => {
                this.execution = null
            })
        },
        bulkDeleteExecution(options) {
            return this.$http.delete(`${apiUrl(this)}/executions/by-ids`, {data: options.executionsId, params: {...options}})
        },
        queryDeleteExecution(options) {
            return this.$http.delete(`${apiUrl(this)}/executions/by-query`, {params: options})
        },
        followExecution(options) {
            return new EventSource(`${apiUrl(this)}/executions/${options.id}/follow`, {withCredentials: true});
        },
        followLogs(options) {
            return new EventSource(`${apiUrl(this)}/logs/${options.id}/follow`, {withCredentials: true});
        },
        loadLogs( options) {
            return this.$http.get(`${apiUrl(this)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data
                }
                this.logs = response.data

                return response.data
            });
        },
        loadMetrics( options) {
            return this.$http.get(`${apiUrl(this)}/metrics/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data
                }
                this.metrics = response.data.results
                this.total =  response.data.total

                return response.data
            });
        },
        downloadLogs(options) {
            return this.$http.get(`${apiUrl(this)}/logs/${options.executionId}/download`, {
                params: options.params
            }).then(response => {
                return response.data
            })
        },
        deleteLogs(options) {
            return this.$http.delete(`${apiUrl(this)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                return response.data
            })
        },
        filePreview(options) {
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

                this.filePreview = data;
                return data;
            })
        },
        setLabels(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/${options.executionId}/labels`,
                options.labels,
                {
                    headers: {
                        "Content-Type": "application/json"
                    }
                })
        },
        querySetLabels(options) {
            return this.$http.post(`${apiUrl(this)}/executions/labels/by-query`, options.data, {
                params: options.params})
        },
        bulkSetLabels(options) {
            return this.$http.post(`${apiUrl(this)}/executions/labels/by-ids`,  options)
        },
        unqueue(options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/unqueue?state=${options.state}`);
        },
        bulkUnqueueExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/unqueue/by-ids?state=${options.newStatus}`,
                options.executionsId
            )
        },
        queryUnqueueExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/unqueue/by-query?state=${options.newStatus}`,
                {},
                {params: options}
            )
        },
        forceRun(options) {
            return this.$http.post(`${apiUrl(this)}/executions/${options.id}/force-run`);
        },
        bulkForceRunExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/force-run/by-ids`,
                options.executionsId
            )
        },
        queryForceRunExecution(options) {
            return this.$http.post(
                `${apiUrl(this)}/executions/force-run/by-query`,
                {},
                {params: options}
            )
        },
        loadFlowForExecution(options) {
            const revision = options.revision ? `?revision=${options.revision}` : "";
            return this.$http.get(`${apiUrl(this)}/executions/flows/${options.namespace}/${options.flowId}${revision}`)
                .then(response => {
                    this.flow = response.data
                    return response.data;
                });
        },
        loadFlowForExecutionByExecutionId(options) {
            return this.$http.get(`${apiUrl(this)}/executions/${options.id}/flow`)
                .then(response => {
                    this.flow = response.data
                    return response.data;
                });
        },
        loadGraph(options) {
            const params = options.params ? options.params : {};
            return axios.get(`${apiUrl(this)}/executions/${options.id}/graph`, {params, withCredentials: true, paramsSerializer: {indexes: null}})
                .then(response => {
                    this.flowGraph = response.data
                })
        },
        loadNamespaces() {
            return this.$http.get(`${apiUrl(this)}/executions/namespaces`)
                .then(response => {
                    this.namespaces = response.data
                })
        },
        loadFlowsExecutable(options) {
            return this.$http.get(`${apiUrl(this)}/executions/namespaces/${options.namespace}/flows`)
                .then(response => {
                    this.flowsExecutable = response.data
                })
        },
        loadLatestExecutions(options) {
            return this.$http.post(`${apiUrl(this)}/executions/latest`, options.flowFilters).then(response => {
                return response.data
            })
        },
        // mutations
        addSubflowExecution(params) {
            this.subflowsExecutions[params.subflow] = params.execution
        },
        removeSubflowExecution(subflow) {
            delete this.subflowsExecutions[subflow]
        },
        resetLogs() {
            this.logs = {results:[], total:0}
        },
        appendLogs(logs) {
            this.logs.results = this.logs.results.concat(logs.results)
        },
        appendFollowedLogs(logs) {
            this.logs.results.push(logs)
            this.logs.total = this.logs.results.length
        },
    },
})
