import YamlUtils from "../utils/yamlUtils";
import Utils from "../utils/utils";

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
        flowGraph: undefined,
        flowGraphParam: undefined,
        revisions: undefined,
    },

    actions: {
        findFlows({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`/api/v1/flows/search${sortString}`, {
                params: options
            }).then(response => {
                commit("setFlows", response.data.results)
                commit("setTotal", response.data.total)

                return response.data;
            })
        },
        searchFlows({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`/api/v1/flows/source${sortString}`, {
                params: options
            }).then(response => {
                commit("setSearch", response.data.results)
                commit("setTotal", response.data.total)

                return response.data;
            })
        },
        loadFlow({commit}, options) {
            return this.$http.get(`/api/v1/flows/${options.namespace}/${options.id}?source=true`,
                {
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
                        commit("setFlow", JSON.parse(response.data.source));
                    } else {
                        commit("setFlow", response.data);
                    }

                    return response.data;
                })
        },
        loadTask({commit}, options) {
            return this.$http.get(`/api/v1/flows/${options.namespace}/${options.id}/tasks/${options.taskId}${options.revision ? "?revision=" + options.revision : ""}`).then(response => {
                commit("setTask", response.data)

                return response.data;
            })
        },
        saveFlow({commit, dispatch}, options) {
            const flowData = YamlUtils.parse(options.flow)
            return this.$http.put(`/api/v1/flows/${flowData.namespace}/${flowData.id}`, options.flow, textYamlHeader)
                .then(response => {
                    if (response.status >= 300) {
                        return Promise.reject(new Error("Server error on flow save"))
                    } else {
                        commit("setFlow", response.data);

                        return response.data;
                    }
                })
                .then(flow => {
                    dispatch("loadGraph", flow);

                    return flow;
                })
        },
        updateFlowTask({commit, dispatch}, options) {
            return this.$http
                .patch(`/api/v1/flows/${options.flow.namespace}/${options.flow.id}/${options.task.id}`, options.task).then(response => {
                    commit("setFlow", response.data)

                    return response.data;
                })
                .then(flow => {
                    dispatch("loadGraph", flow);

                    return flow;
                })
        },
        createFlow({commit}, options) {
            return this.$http.post("/api/v1/flows", options.flow, textYamlHeader).then(response => {
                commit("setFlow", response.data);

                return response.data;
            })
        },
        deleteFlow({commit}, flow) {
            return this.$http.delete(`/api/v1/flows/${flow.namespace}/${flow.id}`).then(() => {
                commit("setFlow", null)
            })
        },
        loadGraph({commit}, flow) {
            return this.$http.get(`/api/v1/flows/${flow.namespace}/${flow.id}/graph?revision=${flow.revision}`).then(response => {
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
            return this.$http.post(`/api/v1/flows/graph`, options.flow, textYamlHeader).then(response => {
                commit("setFlowGraph", response.data)

                let flow = YamlUtils.parse(options.flow);
                flow.source = options.flow;

                commit("setFlow", flow)
                commit("setFlowGraphParam", {
                    namespace: flow.namespace,
                    id: flow.id,
                    revision: flow.revision
                })

                return response.data;
            })
        },
        loadRevisions({commit}, options) {
            return this.$http.get(`/api/v1/flows/${options.namespace}/${options.id}/revisions`).then(response => {
                commit("setRevisions", response.data)
                return response.data;
            })
        },
        exportFlowByIds(_, options) {
            return this.$http.post("/api/v1/flows/export/by-ids", options.ids, {responseType: "blob"})
                .then(response => {
                    const blob = new Blob([response.data], {type: "application/octet-stream"});
                    const url = window.URL.createObjectURL(blob)
                    Utils.downloadUrl(url, "flows.zip");
                });
        },
        exportFlowByQuery(_, options) {
            return this.$http.get("/api/v1/flows/export/by-query", {params: options})
                .then(response => {
                    Utils.downloadUrl(response.request.responseURL, "flows.zip");
                });
        },
        importFlows(_, options) {
            return this.$http.post("/api/v1/flows/import", options, {headers: {"Content-Type": "multipart/form-data"}})
        }
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
            if (state.flowGraph !== undefined && state.flowGraphParam && flow) {
                if (state.flowGraphParam.namespace !== flow.namespace || state.flowGraphParam.id !== flow.id) {
                    state.flowGraph = undefined
                }
            }

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
        setFlowGraph(state, flowGraph) {
            state.flowGraph = flowGraph
        },
    },
    getters: {
        flow(state) {
            if (state.flow) {
                return state.flow;
            }
        },
    }
}
