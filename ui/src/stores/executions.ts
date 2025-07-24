import axios from "axios";
import {defineStore} from "pinia";
import {ref} from "vue";
import {apiUrl} from "override/utils/route";
import Utils from "../utils/utils";
import {useStore, Store} from "vuex";
import {useCoreStore} from "./core";
import {throttle} from "lodash";
import {useRoute} from "vue-router";
import {CLUSTER_PREFIX} from "@kestra-io/ui-libs/src/utils/constants.ts";

interface LogsState {
    total: number;
    results: any[];
}

export interface Execution{
    id: string;
    taskRunList:  {
        id: string,
        taskId: string,
        value?: string
    }[]
    state: {
        current: string;
        history: string;
        startDate: string;
        duration: string;
    }
}

export const useExecutionsStore = defineStore("executions", () => {
    // State
    const executions = ref<Execution[] | undefined>(undefined);
    const execution = ref<Execution | undefined>(undefined);
    const taskRun = ref<any | undefined>(undefined);
    const total = ref<number>(0);
    const logs = ref<LogsState>({
        total: 0,
        results: []
    });
    const metrics = ref<any[]>([]);
    const metricsTotal = ref<number>(0);
    const subflowsExecutions = ref<Record<string, any>>({});
    const flow = ref<any | undefined>(undefined);
    const flowGraph = ref<any | undefined>(undefined);
    const namespaces = ref<string[]>([]);
    const flowsExecutable = ref<any[]>([]);

    const store = useStore() as Store<any> & {
        $http: {
            post: (url: string, data?: any, config?: any) => Promise<any>;
            get: (url: string, config?: any) => Promise<any>;
            delete: (url: string, config?: any) => Promise<any>;
        }
    };

    const coreStore = useCoreStore();

    // Actions
    const restartExecution = (options: { executionId: string; revision?: number }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/${options.executionId}/restart`,
            null,
            {
                params: {
                    revision: options.revision
                }
            })
    }

    const bulkRestartExecution = (options: { executionsId: string[] }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/restart/by-ids`,
            options.executionsId
        )
    }

    const queryRestartExecution = (options: Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/restart/by-query`,
            {},
            {params: options}
        )
    }

    const bulkResumeExecution = (options: { executionsId: string[] }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/resume/by-ids`,
            options.executionsId
        )
    }

    const queryResumeExecution = (options: Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/resume/by-query`,
            {},
            {params: options}
        )
    }

    const bulkReplayExecution = (options: { executionsId: string[] } & Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/replay/by-ids`,
            options.executionsId,
            {params: options}
        )
    }

    const bulkChangeExecutionStatus = (options: { executionsId: string[]; newStatus: string }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/change-status/by-ids`,
            options.executionsId,
            {
                params: {
                    newStatus: options.newStatus
                }
            }
        )
    }

    const queryReplayExecution = (options: Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/replay/by-query`,
            {},
            {params: options}
        )
    }

    const queryChangeExecutionStatus = (options: Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/change-status/by-query`,
            {},
            {params: options}
        )
    }

    const replayExecution = (options: { executionId: string; taskRunId?: string; revision?: number, breakpoints?: string[] }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/${options.executionId}/replay`,
            null,
            {
                params: {
                    taskRunId: options.taskRunId,
                    revision: options.revision,
                    breakpoints: options.breakpoints ? options.breakpoints : undefined
                }
            })
    }

    const changeExecutionStatus = (options: { executionId: string; state: string }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/${options.executionId}/change-status`,
            null,
            {
                params: {
                    status: options.state
                }
            })
    }

    const changeStatus = (options: { executionId: string; taskRunId?: string; state: string }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/${options.executionId}/state`,
            {
                taskRunId: options.taskRunId,
                state: options.state,
            })
    }

    const kill = (options: { id: string; isOnKillCascade?: boolean }) => {
        return store.$http.delete(`${apiUrl(store)}/executions/${options.id}/kill?isOnKillCascade=${options.isOnKillCascade}`);
    }

    const bulkKill = (options: { executionsId: string[] }) => {
        return store.$http.delete(`${apiUrl(store)}/executions/kill/by-ids`, {data: options.executionsId});
    }

    const queryKill = (options: Record<string, any>) => {
        return store.$http.delete(`${apiUrl(store)}/executions/kill/by-query`, {params: options});
    }

    const resume = (options: { id: string; formData: any }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.id}/resume`, Utils.toFormData(options.formData), {
            timeout: 60 * 60 * 1000,
            headers: {
                "content-type": "multipart/form-data"
            }
        });
    }

    const validateResume = (options: { id: string; formData: any }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.id}/resume/validate`, Utils.toFormData(options.formData), {
            timeout: 60 * 60 * 1000,
            headers: {
                "content-type": "multipart/form-data"
            }
        });
    }

    const pause = (options: { id: string }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.id}/pause`);
    }

    const bulkPauseExecution = (options: { executionsId: string[] }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/pause/by-ids`,
            options.executionsId
        )
    }

    const queryPauseExecution = (options: Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/pause/by-query`,
            {},
            {params: options}
        )
    }

    const loadExecution = (options: { id: string }) => {
        return store.$http.get(`${apiUrl(store)}/executions/${options.id}`).then(response => {
            execution.value = response.data;
            return response.data;
        })
    }

    const findExecutions = (options: { commit?: boolean } & Record<string, any>) => {
        return store.$http.get(`${apiUrl(store)}/executions/search`, {params: options}).then(response => {
            if (options.commit !== false) {
                executions.value = response.data.results;
                total.value = response.data.total;
            }
            return response.data;
        })
    }

    const validateExecution = (options: { namespace: string; id: string; formData: any; labels?: string[]; scheduleDate?: string }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.namespace}/${options.id}/validate`, Utils.toFormData(options.formData), {
            timeout: 60 * 60 * 1000,
            headers: {
                "content-type": "multipart/form-data"
            },
            params: {
                labels: options.labels ?? [],
                scheduleDate: options.scheduleDate
            }
        })
    }

    const triggerExecution = (options: {
        namespace: string;
        id: string;
        formData: any;
        kind: "PLAYGROUND" | "NORMAL"
        breakpoints?: string[];
        labels?: string[];
        scheduleDate?: string,
    }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.namespace}/${options.id}`, Utils.toFormData(options.formData), {
            timeout: 60 * 60 * 1000,
            headers: {
                "content-type": "multipart/form-data"
            },
            params: {
                labels: options.labels ?? [],
                scheduleDate: options.scheduleDate,
                kind: options.kind,
                breakpoints: options.breakpoints ? options.breakpoints.join(",") : undefined
            }
        })
    }

    const deleteExecution = (options: { id: string; deleteLogs?: boolean; deleteMetrics?: boolean; deleteStorage?: boolean }) => {
        const {id, deleteLogs, deleteMetrics, deleteStorage} = options;
        const qs = Object.entries({deleteLogs, deleteMetrics, deleteStorage})
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
            .join("&");

        return store.$http.delete(`${apiUrl(store)}/executions/${id}?${qs}`).then(() => {
            execution.value = undefined;
        })
    }

    const bulkDeleteExecution = (options: { executionsId: string[] } & Record<string, any>) => {
        return store.$http.delete(`${apiUrl(store)}/executions/by-ids`, {data: options.executionsId, params: {...options}})
    }

    const queryDeleteExecution = (options: Record<string, any>) => {
        return store.$http.delete(`${apiUrl(store)}/executions/by-query`, {params: options})
    }

    const sse = ref<EventSource | undefined>(undefined);

    function closeSSE() {
        if (sse.value) {
            sse.value.close();
            sse.value = undefined;
        }
    }

    const route = useRoute();

    const throttledExecutionUpdate = throttle((executionEvent: MessageEvent) => {
        const _execution = JSON.parse(executionEvent.data);

        const _flow = flow.value;

        if ((!_flow ||
            _execution.flowId !== _flow.id ||
            _execution.namespace !== _flow.namespace ||
            _execution.flowRevision !== _flow.revision)
        ) {
            loadFlowForExecutionByExecutionId(
                {
                    id: _execution.id,
                    revision: route.query.revision?.toString()
                }
            ).then(() => {
                execution.value = _execution
            });
        }

        execution.value = _execution;
    }, 500);

    const followExecution = (options: { id: string }, translate: (itn: string) => string) => {
        closeSSE();
        const serverSentEventSource = new EventSource(`${apiUrl(store)}/executions/${options.id}/follow`, {withCredentials: true});
        sse.value = serverSentEventSource;
        serverSentEventSource.onmessage = (executionEvent) => {
            const isEnd = executionEvent && executionEvent.lastEventId === "end";
            if (isEnd) {
                closeSSE();
            }
            // we are receiving a first "fake" event to force initializing the connection: ignoring it
            if (executionEvent.lastEventId !== "start") {
                throttledExecutionUpdate(executionEvent);
            }
            if (isEnd) {
                throttledExecutionUpdate.flush();
            }
        }

        // sse.onerror doesn't return the details of the error
        // but as our emitter can only throw an error on 404
        // we can safely assume that the error is a 404
        // if execution is not defined
        serverSentEventSource.onerror = () => {
            if (!execution.value) {
                coreStore.message = {
                    variant: "error",
                    title: translate("error"),
                    message: translate("errors.404.flow or execution"),
                };
            } else {
                coreStore.message = {
                    variant: "error",
                    title: translate("error"),
                    message: translate("something_went_wrong.loading_execution"),
                };
            }
        }

        return Promise.resolve(sse.value);
    }

    function followExecutionDependencies(options: { id: string; expandAll?: boolean }) {
        return new EventSource(`${apiUrl(store)}/executions/${options.id}/follow-dependencies${options.expandAll ? "?expandAll=true" : ""}`, {withCredentials: true});
    }

    const followLogs = (options: { id: string }) => {
        return Promise.resolve(new EventSource(`${apiUrl(store)}/logs/${options.id}/follow`, {withCredentials: true}));
    }

    const loadLogs = (options: { executionId: string; params?: Record<string, any>; store?: boolean }) => {
        return store.$http.get(`${apiUrl(store)}/logs/${options.executionId}`, {
            params: options.params
        }).then(response => {
            if (options.store === false) {
                return response.data;
            }
            logs.value = response.data;
            return response.data;
        });
    }

    const loadMetrics = (options: { executionId: string; params?: Record<string, any>; store?: boolean }) => {
        return store.$http.get(`${apiUrl(store)}/metrics/${options.executionId}`, {
            params: options.params
        }).then(response => {
            if (options.store === false) {
                return response.data;
            }
            metrics.value = response.data.results;
            total.value = response.data.total;
            return response.data;
        });
    }

    const downloadLogs = (options: { executionId: string; params?: Record<string, any> }) => {
        return store.$http.get(`${apiUrl(store)}/logs/${options.executionId}/download`, {
            params: options.params
        }).then(response => {
            return response.data;
        })
    }

    const deleteLogs = (options: { executionId: string; params?: Record<string, any> }) => {
        return store.$http.delete(`${apiUrl(store)}/logs/${options.executionId}`, {
            params: options.params
        }).then(response => {
            return response.data;
        })
    }

    const _filePreview = ref<any | undefined>(undefined);
    const filePreview = (options: { executionId: string } & Record<string, any>) => {
        return store.$http.get(`${apiUrl(store)}/executions/${options.executionId}/file/preview`, {
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

            _filePreview.value = data;
            return data;
        })
    }

    const setLabels = (options: { executionId: string; labels: any }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/${options.executionId}/labels`,
            options.labels,
            {
                headers: {
                    "Content-Type": "application/json"
                }
            })
    }

    const querySetLabels = (options: { data: any; params: Record<string, any> }) => {
        return store.$http.post(`${apiUrl(store)}/executions/labels/by-query`, options.data, {
            params: options.params
        })
    }

    const bulkSetLabels = (options: any) => {
        return store.$http.post(`${apiUrl(store)}/executions/labels/by-ids`, options)
    }

    const unqueue = (options: { id: string; state: string }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.id}/unqueue?state=${options.state}`);
    }

    const bulkUnqueueExecution = (options: { executionsId: string[]; newStatus: string }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/unqueue/by-ids?state=${options.newStatus}`,
            options.executionsId
        )
    }

    const queryUnqueueExecution = (options: { newStatus: string } & Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/unqueue/by-query?state=${options.newStatus}`,
            {},
            {params: options}
        )
    }

    const forceRun = (options: { id: string }) => {
        return store.$http.post(`${apiUrl(store)}/executions/${options.id}/force-run`);
    }

    const bulkForceRunExecution = (options: { executionsId: string[] }) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/force-run/by-ids`,
            options.executionsId
        )
    }

    const queryForceRunExecution = (options: Record<string, any>) => {
        return store.$http.post(
            `${apiUrl(store)}/executions/force-run/by-query`,
            {},
            {params: options}
        )
    }

    const loadFlowForExecution = (options: { namespace: string; flowId: string; revision?: number }) => {
        const revision = options.revision ? `?revision=${options.revision}` : "";
        return store.$http.get(`${apiUrl(store)}/executions/flows/${options.namespace}/${options.flowId}${revision}`)
            .then(response => {
                flow.value = response.data;
                return response.data;
            });
    }

    const loadFlowForExecutionByExecutionId = (options: { id: string, revision?: string }) => {
        return store.$http.get(`${apiUrl(store)}/executions/${options.id}/flow`)
            .then(response => {
                flow.value = response.data;
                return response.data;
            });
    }

    const fetchGraph = (options: { id: string; params?: Record<string, any> }) => {
        const params = options.params ? options.params : {};
        return axios.get(`${apiUrl(store)}/executions/${options.id}/graph`, {params, withCredentials: true, paramsSerializer: {indexes: null}})
            .then(response => {
                return response.data;
            })
    }

    function loadGraph(options: { id: string; params?: Record<string, any> }) {
        return fetchGraph(options).then(graph => {
            // force refresh - Create a new object reference to trigger reactivity
            flowGraph.value = Object.assign({}, graph);
        });
    }

    function isUnused(nodeByUid: Record<string, any>, nodeUid: string): boolean {
            const nodeToCheck = nodeByUid[nodeUid];

            if(!nodeToCheck) {
                return false;
            }

            if(!nodeToCheck.task) {
                // check if parent is unused (current node is probably a cluster root or end)
                const splitUid = nodeToCheck.uid.split(".");
                splitUid.pop();
                return isUnused(nodeByUid, splitUid.join("."));
            }

            if (!nodeToCheck.executionId) {
                return true;
            }

            const nodeExecution = nodeToCheck.executionId === execution.value?.id ? execution.value
                : Object.values(subflowsExecutions.value).filter(execution => execution.id === nodeToCheck.executionId)?.[0];

            if (!nodeExecution) {
                return true;
            }

            return !nodeExecution.taskRunList?.some((taskRun: { taskId: string }) => taskRun.taskId === nodeToCheck.task?.id);


        }

    const loadAugmentedGraph = async (options: { id: string; params?: Record<string, any> }) => {
        const params = options.params ? options.params : {};
        const graph: {
            nodes: any[];
            edges: any[];
            clusters?: any[];
        } = await fetchGraph({id: options.id, params});
        // Augment the graph with additional properties

        const subflowPaths = graph.clusters
            ?.map(c => c.cluster)
            ?.filter(cluster => cluster.type.endsWith("SubflowGraphCluster"))
            ?.map(cluster => cluster.uid.replace(CLUSTER_PREFIX, ""))
            ?? [];
        const nodeByUid: Record<string, any> = {};

        graph.nodes
            // lowest depth first to be available in nodeByUid map for child-to-parent unused check
            .sort((a, b) => a.uid.length - b.uid.length)
            .forEach(node => {
                nodeByUid[node.uid] = node;

                const parentSubflow = subflowPaths.filter(subflowPath => node.uid.startsWith(subflowPath + "."))
                    .sort((a, b) => b.length - a.length)?.[0]

                if(parentSubflow) {
                    if(parentSubflow in subflowsExecutions.value) {
                        node.executionId = subflowsExecutions.value[parentSubflow]?.id;
                    }

                    return;
                }

                node.executionId = options.id;

                // reduce opacity for cluster root & end
                if(!node.task && isUnused(nodeByUid, node.uid)) {
                    node.unused = true;
                }
            });

        graph.edges
            // keep only unused (or skipped) paths
            .filter(edge => {
                return isUnused(nodeByUid, edge.target) || isUnused(nodeByUid, edge.source);
            }).forEach(edge => edge.unused = true);

        // force refresh - Create a new object reference to trigger reactivity
        flowGraph.value = Object.assign({}, graph);

        return graph;
    }

    const loadNamespaces = () => {
        return store.$http.get(`${apiUrl(store)}/executions/namespaces`)
            .then(response => {
                namespaces.value = response.data;
            })
    }

    const loadFlowsExecutable = (options: { namespace: string }) => {
        return store.$http.get(`${apiUrl(store)}/executions/namespaces/${options.namespace}/flows`)
            .then(response => {
                flowsExecutable.value = response.data;
            })
    }

    const loadLatestExecutions = (options: { flowFilters: any }) => {
        return store.$http.post(`${apiUrl(store)}/executions/latest`, options.flowFilters).then(response => {
            return response.data;
        })
    }

    // mutations
    const addSubflowExecution = (params: { subflow: string; execution: any }) => {
        subflowsExecutions.value[params.subflow] = params.execution;
    }

    const removeSubflowExecution = (subflow: string) => {
        delete subflowsExecutions.value[subflow];
    }

    const resetLogs = () => {
        logs.value = {results: [], total: 0};
    }

    const appendLogs = (logsData: { results: any[] }) => {
        logs.value.results = logs.value.results.concat(logsData.results);
    }

    const appendFollowedLogs = (logsData: any) => {
        logs.value.results.push(logsData);
        logs.value.total = logs.value.results.length;
    }

    const getFlowExecutions = ({namespace, flowId}: { namespace: string; flowId: string }) => {
        return store.$http.get(`${apiUrl(store)}/executions`, {
            params: {
                namespace,
                flowId,
            }
        }).then(response => {
            executions.value = response.data.results;
            total.value = response.data.total;
            return response.data;
        });
    }

    return {
        // State
        executions,
        execution,
        taskRun,
        total,
        logs,
        metrics,
        metricsTotal,
        subflowsExecutions,
        flow,
        flowGraph,
        namespaces,
        flowsExecutable,
        // Actions
        restartExecution,
        bulkRestartExecution,
        queryRestartExecution,
        bulkResumeExecution,
        queryResumeExecution,
        bulkReplayExecution,
        bulkChangeExecutionStatus,
        queryReplayExecution,
        queryChangeExecutionStatus,
        replayExecution,
        changeExecutionStatus,
        changeStatus,
        kill,
        bulkKill,
        queryKill,
        resume,
        validateResume,
        pause,
        bulkPauseExecution,
        queryPauseExecution,
        loadExecution,
        findExecutions,
        validateExecution,
        triggerExecution,
        deleteExecution,
        bulkDeleteExecution,
        queryDeleteExecution,
        closeSSE,
        followExecution,
        followExecutionDependencies,
        followLogs,
        loadLogs,
        loadMetrics,
        downloadLogs,
        deleteLogs,
        filePreview,
        setLabels,
        querySetLabels,
        bulkSetLabels,
        unqueue,
        bulkUnqueueExecution,
        queryUnqueueExecution,
        forceRun,
        bulkForceRunExecution,
        queryForceRunExecution,
        loadFlowForExecution,
        loadFlowForExecutionByExecutionId,
        loadGraph,
        loadAugmentedGraph,
        loadNamespaces,
        loadFlowsExecutable,
        loadLatestExecutions,
        addSubflowExecution,
        removeSubflowExecution,
        resetLogs,
        appendLogs,
        appendFollowedLogs,
        getFlowExecutions,
    };
});