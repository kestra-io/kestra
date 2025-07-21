import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import Utils from "../utils/utils";

interface LogsState {
    total: number;
    results: any[];
}

interface Execution{
    taskRunList:  {
        id: string,
        taskId: string,
        value?: string
    }[]
}

interface ExecutionsState {
    executions: Execution[] | undefined;
    execution: Execution | undefined;
    taskRun: any | undefined;
    total: number;
    logs: LogsState;
    metrics: any[];
    metricsTotal: number;
    filePreview: any | undefined;
    subflowsExecutions: Record<string, any>;
    flow: any | undefined;
    flowGraph: any | undefined;
    namespaces: string[];
    flowsExecutable: any[];
}

export const useExecutionsStore = defineStore("executions", {
    state: (): ExecutionsState => ({
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
        restartExecution(options: { executionId: string; revision?: number }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/${options.executionId}/restart`,
                null,
                {
                    params: {
                        revision: options.revision
                    }
                })
        },
        bulkRestartExecution(options: { executionsId: string[] }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/restart/by-ids`,
                options.executionsId
            )
        },
        queryRestartExecution(options: Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/restart/by-query`,
                {},
                {params: options}
            )
        },
        bulkResumeExecution(options: { executionsId: string[] }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/resume/by-ids`,
                options.executionsId
            )
        },
        queryResumeExecution(options: Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/resume/by-query`,
                {},
                {params: options}
            )
        },
        bulkReplayExecution(options: { executionsId: string[] } & Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/replay/by-ids`,
                options.executionsId,
                {params: options}
            )
        },
        bulkChangeExecutionStatus(options: { executionsId: string[]; newStatus: string }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/change-status/by-ids`,
                options.executionsId,
                {
                    params: {
                        newStatus: options.newStatus
                    }
                }
            )
        },
        queryReplayExecution(options: Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/replay/by-query`,
                {},
                {params: options}
            )
        },
        queryChangeExecutionStatus(options: Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/change-status/by-query`,
                {},
                {params: options}
            )
        },
        replayExecution(options: { executionId: string; taskRunId?: string; revision?: number }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/${options.executionId}/replay`,
                null,
                {
                    params: {
                        taskRunId: options.taskRunId,
                        revision: options.revision
                    }
                })
        },
        changeExecutionStatus(options: { executionId: string; state: string }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/${options.executionId}/change-status`,
                null,
                {
                    params: {
                        status: options.state
                    }
                })
        },
        changeStatus(options: { executionId: string; taskRunId?: string; state: string }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/${options.executionId}/state`,
                {
                    taskRunId: options.taskRunId,
                    state: options.state,
                })
        },
        kill(options: { id: string; isOnKillCascade?: boolean }) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/executions/${options.id}/kill?isOnKillCascade=${options.isOnKillCascade}`);
        },
        bulkKill(options: { executionsId: string[] }) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/executions/kill/by-ids`, {data: options.executionsId});
        },
        queryKill(options: Record<string, any>) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/executions/kill/by-query`, {params: options});
        },
        resume(options: { id: string; formData: any }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.id}/resume`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            });
        },
        validateResume(options: { id: string; formData: any }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.id}/resume/validate`, Utils.toFormData(options.formData), {
                timeout: 60 * 60 * 1000,
                headers: {
                    "content-type": "multipart/form-data"
                }
            });
        },
        pause(options: { id: string }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.id}/pause`);
        },
        bulkPauseExecution(options: { executionsId: string[] }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/pause/by-ids`,
                options.executionsId
            )
        },
        queryPauseExecution(options: Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/pause/by-query`,
                {},
                {params: options}
            )
        },
        loadExecution(options: { id: string }) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/${options.id}`).then(response => {
                this.execution = response.data;
                return response.data;
            })
        },
        findExecutions(options: { commit?: boolean } & Record<string, any>) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/search`, {params: options}).then(response => {
                if (options.commit !== false) {
                    this.executions = response.data.results;
                    this.total = response.data.total;
                }
                return response.data;
            })
        },
        validateExecution(options: { namespace: string; id: string; formData: any; labels?: string[]; scheduleDate?: string }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.namespace}/${options.id}/validate`, Utils.toFormData(options.formData), {
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
        triggerExecution(options: { namespace: string; id: string; formData: any; labels?: string[]; scheduleDate?: string }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.namespace}/${options.id}`, Utils.toFormData(options.formData), {
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
        deleteExecution(options: { id: string; deleteLogs?: boolean; deleteMetrics?: boolean; deleteStorage?: boolean }) {
            const {id, deleteLogs, deleteMetrics, deleteStorage} = options;
            const qs = Object.entries({deleteLogs, deleteMetrics, deleteStorage})
                .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
                .join("&");

            return this.$http.delete(`${apiUrl(this.vuexStore)}/executions/${id}?${qs}`).then(() => {
                this.execution = undefined;
            })
        },
        bulkDeleteExecution(options: { executionsId: string[] } & Record<string, any>) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/executions/by-ids`, {data: options.executionsId, params: {...options}})
        },
        queryDeleteExecution(options: Record<string, any>) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/executions/by-query`, {params: options})
        },
        followExecution(options: { id: string }) {
            return Promise.resolve(new EventSource(`${apiUrl(this.vuexStore)}/executions/${options.id}/follow`, {withCredentials: true}));
        },
        followExecutionDependencies(options: { id: string; expandAll?: boolean }) {
            return Promise.resolve(new EventSource(`${apiUrl(this.vuexStore)}/executions/${options.id}/follow-dependencies${options.expandAll ? "?expandAll=true" : ""}`, {withCredentials: true}));
        },
        followLogs(options: { id: string }) {
            return Promise.resolve(new EventSource(`${apiUrl(this.vuexStore)}/logs/${options.id}/follow`, {withCredentials: true}));
        },
        loadLogs(options: { executionId: string; params?: Record<string, any>; store?: boolean }) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data;
                }
                this.logs = response.data;
                return response.data;
            });
        },
        loadMetrics(options: { executionId: string; params?: Record<string, any>; store?: boolean }) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/metrics/${options.executionId}`, {
                params: options.params
            }).then(response => {
                if (options.store === false) {
                    return response.data;
                }
                this.metrics = response.data.results;
                this.total = response.data.total;
                return response.data;
            });
        },
        downloadLogs(options: { executionId: string; params?: Record<string, any> }) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/logs/${options.executionId}/download`, {
                params: options.params
            }).then(response => {
                return response.data;
            })
        },
        deleteLogs(options: { executionId: string; params?: Record<string, any> }) {
            return this.$http.delete(`${apiUrl(this.vuexStore)}/logs/${options.executionId}`, {
                params: options.params
            }).then(response => {
                return response.data;
            })
        },
        filePreview(options: { executionId: string } & Record<string, any>) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/${options.executionId}/file/preview`, {
                params: options
            }).then(response => {
                let data = {...response.data};

                // WORKAROUND, related to https://github.com/kestra-io/plugin-aws/issues/456
                if (data.extension === "ion") {
                    const notObjects = data.content.some((e: any) => typeof e !== "object");

                    if (notObjects) {
                        const content = data.content.length === 1 ? data.content[0] : data.content.join("\n");
                        data = {...data, type: "TEXT", content};
                    }
                }

                this.filePreview = data;
                return data;
            })
        },
        setLabels(options: { executionId: string; labels: any }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/${options.executionId}/labels`,
                options.labels,
                {
                    headers: {
                        "Content-Type": "application/json"
                    }
                })
        },
        querySetLabels(options: { data: any; params: Record<string, any> }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/labels/by-query`, options.data, {
                params: options.params
            })
        },
        bulkSetLabels(options: any) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/labels/by-ids`, options)
        },
        unqueue(options: { id: string; state: string }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.id}/unqueue?state=${options.state}`);
        },
        bulkUnqueueExecution(options: { executionsId: string[]; newStatus: string }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/unqueue/by-ids?state=${options.newStatus}`,
                options.executionsId
            )
        },
        queryUnqueueExecution(options: { newStatus: string } & Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/unqueue/by-query?state=${options.newStatus}`,
                {},
                {params: options}
            )
        },
        forceRun(options: { id: string }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/${options.id}/force-run`);
        },
        bulkForceRunExecution(options: { executionsId: string[] }) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/force-run/by-ids`,
                options.executionsId
            )
        },
        queryForceRunExecution(options: Record<string, any>) {
            return this.$http.post(
                `${apiUrl(this.vuexStore)}/executions/force-run/by-query`,
                {},
                {params: options}
            )
        },
        loadFlowForExecution(options: { namespace: string; flowId: string; revision?: number }) {
            const revision = options.revision ? `?revision=${options.revision}` : "";
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/flows/${options.namespace}/${options.flowId}${revision}`)
                .then(response => {
                    this.flow = response.data;
                    return response.data;
                });
        },
        loadFlowForExecutionByExecutionId(options: { id: string }) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/${options.id}/flow`)
                .then(response => {
                    this.flow = response.data;
                    return response.data;
                });
        },
        loadGraph(options: { id: string; params?: Record<string, any> }) {
            const params = options.params ? options.params : {};
            return axios.get(`${apiUrl(this.vuexStore)}/executions/${options.id}/graph`, {params, withCredentials: true, paramsSerializer: {indexes: null}})
                .then(response => {
                    this.flowGraph = response.data;
                })
        },
        loadNamespaces() {
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/namespaces`)
                .then(response => {
                    this.namespaces = response.data;
                })
        },
        loadFlowsExecutable(options: { namespace: string }) {
            return this.$http.get(`${apiUrl(this.vuexStore)}/executions/namespaces/${options.namespace}/flows`)
                .then(response => {
                    this.flowsExecutable = response.data;
                })
        },
        loadLatestExecutions(options: { flowFilters: any }) {
            return this.$http.post(`${apiUrl(this.vuexStore)}/executions/latest`, options.flowFilters).then(response => {
                return response.data;
            })
        },
        // mutations
        addSubflowExecution(params: { subflow: string; execution: any }) {
            this.subflowsExecutions[params.subflow] = params.execution;
        },
        removeSubflowExecution(subflow: string) {
            delete this.subflowsExecutions[subflow];
        },
        resetLogs() {
            this.logs = {results: [], total: 0};
        },
        appendLogs(logs: { results: any[] }) {
            this.logs.results = this.logs.results.concat(logs.results);
        },
        appendFollowedLogs(logs: any) {
            this.logs.results.push(logs);
            this.logs.total = this.logs.results.length;
        },
    },
});