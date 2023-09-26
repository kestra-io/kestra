import axios from "axios";
import YamlUtils from "../utils/yamlUtils";
import Utils from "../utils/utils";
import {apiUrl} from "override/utils/route";

const textYamlHeader = {
    headers: {
        "Content-Type": "application/x-yaml"
    }
}
export default {
    namespaced: true,
    state: {
        flows: undefined,
        flow: undefined,
        task: undefined,
        search: undefined,
        total: 0,
        overallTotal: undefined,
        flowGraph: undefined,
        flowGraphParam: undefined,
        revisions: undefined,
        flowError: undefined,
        flowDeprecations: undefined,
        taskError: undefined,
        metrics: [],
        aggregatedMetrics: undefined,
        tasksWithMetrics: []
    },

    actions: {
        findFlows({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/flows/search${sortString}`, {
                params: options
            }).then(response => {
                commit("setFlows", response.data.results)
                commit("setTotal", response.data.total)
                commit("setOverallTotal", response.data.total)

                return response.data;
            })
        },
        searchFlows({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`${apiUrl(this)}/flows/source${sortString}`, {
                params: options
            }).then(response => {
                commit("setSearch", response.data.results)
                commit("setTotal", response.data.total)

                return response.data;
            })
        },
        loadFlow({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/flows/${options.namespace}/${options.id}?source=true`,
                {
                    params: options,
                    validateStatus: (status) => {
                        return options.deleted ? status === 200 || status === 404 : status === 200;
                    }
                })
                .then(response => {
                    if (response.data.exception) {
                        commit("core/setMessage", {
                            title: "Invalid source code",
                            message: response.data.exception,
                            variant: "danger"
                        }, {root: true});
                        delete response.data.exception;
                    }
                    if(options.store === false) {
                        return response.data;
                    }
                    commit("setFlow", response.data);
                    commit("setOverallTotal", 1)
                    return response.data;
                })
        },
        loadTask({commit}, options) {
            return this.$http.get(
                `${apiUrl(this)}/flows/${options.namespace}/${options.id}/tasks/${options.taskId}${options.revision ? "?revision=" + options.revision : ""}`,
                {
                    validateStatus: (status) => {
                        return status === 200 || status === 404;
                    }
                }
            )
                .then(response => {
                    if (response.status === 200) {
                        commit("setTask", response.data)

                        return response.data;
                    } else {
                        return null;
                    }
                })
        },
        saveFlow({commit, dispatch}, options) {
            const flowData = YamlUtils.parse(options.flow)
            return this.$http.put(`${apiUrl(this)}/flows/${flowData.namespace}/${flowData.id}`, options.flow, textYamlHeader)
                .then(response => {
                    if (response.status >= 300) {
                        return Promise.reject(new Error("Server error on flow save"))
                    } else {
                        commit("setFlow", response.data);

                        return response.data;
                    }
                })
        },
        updateFlowTask({commit, dispatch}, options) {
            return this.$http
                .patch(`${apiUrl(this)}/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                    commit("setFlow", response.data)

                    return response.data;
                })
                .then(flow => {
                    dispatch("loadGraph", {flow});

                    return flow;
                })
        },
        createFlow({commit}, options) {
            return this.$http.post(`${apiUrl(this)}/flows`, options.flow, textYamlHeader).then(response => {
                commit("setFlow", response.data);

                return response.data;
            })
        },
        deleteFlow({commit}, flow) {
            return this.$http.delete(`${apiUrl(this)}/flows/${flow.namespace}/${flow.id}`).then(() => {
                commit("setFlow", null)
            })
        },
        loadGraph({commit}, options) {
            const flow = options.flow;
            const params = options.params ? options.params : {};
            if (flow.revision) {
                params["revision"] = flow.revision;
            }
            return this.$http.get(`${apiUrl(this)}/flows/${flow.namespace}/${flow.id}/graph`, {params}).then(response => {
                commit("setFlowGraph", response.data)
                commit("setFlowGraphParam", {
                    namespace: flow.namespace,
                    id: flow.id,
                    revision: flow.revision
                })

                return response.data;
            })
        },
        loadGraphFromSource({commit}, options) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YamlUtils.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YamlUtils.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return axios.post(`${apiUrl(this)}/flows/graph`, flowSource, {...config})
                .then(response => {
                    if (response.status === 422) {
                        return response;
                    }
                    commit("setFlowGraph", response.data)

                    let flow = YamlUtils.parse(options.flow);
                    flow.source = options.flow;
                    commit("setFlow", flow)
                    commit("setFlowGraphParam", {
                        namespace: flow.namespace ? flow.namespace : "default",
                        id: flow.id ? flow.id : "default",
                        revision: flow.revision
                    })

                    return response;
                }).catch(error => {
                    if(error.response?.status === 404) {
                        commit("core/setMessage", {
                            title: "Couldn't expand subflow",
                            message: error.response.data.message,
                            variant: "danger"
                        }, {root: true});
                    }

                    return Promise.reject(error);
                })
        },
        getGraphFromSourceResponse({commit}, options) {
            const config = options.config ? {...options.config, ...textYamlHeader} : textYamlHeader;
            const flowParsed = YamlUtils.parse(options.flow);
            let flowSource = options.flow
            if (!flowParsed.id || !flowParsed.namespace) {
                flowSource = YamlUtils.updateMetadata(flowSource, {id: "default", namespace: "default"})
            }
            return this.$http.post(`${apiUrl(this)}/flows/graph`, flowSource, {...config})
                .then(response => response.data)
        },
        loadRevisions({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/flows/${options.namespace}/${options.id}/revisions`).then(response => {
                commit("setRevisions", response.data)
                return response.data;
            })
        },
        exportFlowByIds(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/export/by-ids`, options.ids, {responseType: "blob"})
                .then(response => {
                    const blob = new Blob([response.data], {type: "application/octet-stream"});
                    const url = window.URL.createObjectURL(blob)
                    Utils.downloadUrl(url, "flows.zip");
                });
        },
        exportFlowByQuery(_, options) {
            return this.$http.get(`${apiUrl(this)}/flows/export/by-query`, {params: options})
                .then(response => {
                    Utils.downloadUrl(response.request.responseURL, "flows.zip");
                });
        },
        importFlows(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/import`, options, {headers: {"Content-Type": "multipart/form-data"}})
        },
        disableFlowByIds(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/disable/by-ids`, options.ids)
        },
        disableFlowByQuery(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/disable/by-query`, options, {params: options})
        },
        enableFlowByIds(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/enable/by-ids`, options.ids)
        },
        enableFlowByQuery(_, options) {
            return this.$http.post(`${apiUrl(this)}/flows/enable/by-query`, options, {params: options})
        },
        deleteFlowByIds(_, options) {
            return this.$http.delete(`${apiUrl(this)}/flows/delete/by-ids`, {data: options.ids})
        },
        deleteFlowByQuery(_, options) {
            return this.$http.delete(`${apiUrl(this)}/flows/delete/by-query`, {params: options})
        },
        validateFlow({commit}, options) {
            return axios.post(`${apiUrl(this)}/flows/validate`, options.flow, textYamlHeader)
                .then(response => {
                    commit("setFlowError", response.data[0] ? response.data[0].constraints : undefined)
                    commit("setFlowDeprecations", response.data[0] ? response.data[0].deprecationPaths : undefined)
                    return response.data
                })
        },
        validateTask({commit}, options) {
            return axios.post(`${apiUrl(this)}/flows/validate/task`, options.task, {...textYamlHeader, params: {section: options.section}})
                .then(response => {
                    commit("setTaskError", response.data.constraints)
                    return response.data
                })
        },
        loadFlowMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/names/${options.namespace}/${options.id}`)
                .then(response => {
                    commit("setMetrics", response.data)
                    return response.data
                })
        },
        loadTaskMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/names/${options.namespace}/${options.id}/${options.taskId}`)
                .then(response => {
                    commit("setMetrics", response.data)
                    return response.data
                })
        },
        loadTasksWithMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/tasks/${options.namespace}/${options.id}`)
                .then(response => {
                    commit("setTasksWithMetrics", response.data)
                    return response.data
                })
        },
        loadFlowAggregatedMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/aggregates/${options.namespace}/${options.id}/${options.metric}`, {params: options})
                .then(response => {
                    commit("setAggregatedMetric", response.data)
                    return response.data
                })
        },
        loadTaskAggregatedMetrics({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/metrics/aggregates/${options.namespace}/${options.id}/${options.taskId}/${options.metric}`, {params: options})
                .then(response => {
                    commit("setAggregatedMetric", response.data)
                    return response.data
                })
        },
    },
    mutations: {
        setFlows(state, flows) {
            state.flows = flows
        },
        setSearch(state, search) {
            state.search = search
        },
        setRevisions(state, revisions) {
            state.revisions = revisions
        },
        setFlow(state, flow) {
            state.flow = flow;
            // if (state.flowGraph !== undefined && state.flowGraphParam && flow) {
            //     if (state.flowGraphParam.namespace !== flow.namespace || state.flowGraphParam.id !== flow.id) {
            //         state.flowGraph = undefined
            //     }
            // }

        },
        setFlowGraphParam(state, flow) {
            state.flowGraphParam = flow
        },
        setTask(state, task) {
            state.task = task;
        },
        setTrigger(state, {index, trigger}) {
            let flow = state.flow;

            if (flow.triggers === undefined) {
                flow.triggers = []
            }

            flow.triggers[index] = trigger;

            state.flow = {...flow}
        },
        removeTrigger(state, index) {
            let flow = state.flow;
            flow.triggers.splice(index, 1);

            state.flow = {...flow}
        },
        addTrigger(state, trigger) {
            let flow = state.flow;

            if (trigger.backfill === undefined) {
                trigger.backfill = {
                    start: undefined
                }
            }

            if (flow.triggers === undefined) {
                flow.triggers = []
            }

            flow.triggers.push(trigger)

            state.flow = {...flow}
        },
        setTotal(state, total) {
            state.total = total
        },
        setOverallTotal(state, total) {
            state.overallTotal = total
        },
        setFlowGraph(state, flowGraph) {
            state.flowGraph = flowGraph
        },
        setFlowError(state, flowError) {
            state.flowError = flowError
        },
        setFlowDeprecations(state, flowDeprecations) {
            state.flowDeprecations = flowDeprecations
        },
        setTaskError(state, taskError) {
            state.taskError = taskError
        },
        setMetrics(state, metrics) {
            state.metrics = metrics
        },
        setAggregatedMetric(state, aggregatedMetric) {
            state.aggregatedMetric = aggregatedMetric
        },
        setTasksWithMetrics(state, tasksWithMetrics) {
            state.tasksWithMetrics = tasksWithMetrics
        }
    },
    getters: {
        flow(state) {
            if (state.flow) {
                return state.flow;
            }
        },
        flowError(state) {
            if (state.flowError) {
                return state.flowError;
            }
        },
        flowDeprecations(state) {
            if (state.flowDeprecations) {
                return state.flowDeprecations;
            }
        },
        taskError(state) {
            if (state.taskError) {
                return state.taskError;
            }
        }
    }
}
