export default {
    namespaced: true,
    state: {
        flows: undefined,
        flow: undefined,
        task: undefined,
        search: undefined,
        total: 0,
        flowGraph: undefined,
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
            return this.$http.get(`/api/v1/flows/${options.namespace}/${options.id}`).then(response => {
                if (response.data.exception) {
                    commit("core/setMessage", {title: "Invalid source code", message: response.data.exception, variant: "danger"}, {root: true});
                    delete response.data.exception;
                    commit("setFlow", JSON.parse(response.data.source));
                } else {
                    commit("setFlow", response.data)
                }

                return response.data;
            })
        },
        loadTask({commit}, options) {
            return this.$http.get(`/api/v1/flows/${options.namespace}/${options.id}/tasks/${options.taskId}`).then(response => {
                commit("setTask", response.data)

                return response.data;
            })
        },
        saveFlow({commit, dispatch}, options) {
            return this.$http.put(`/api/v1/flows/${options.flow.namespace}/${options.flow.id}`, options.flow)
                .then(response => {
                    if (response.status >= 300) {
                        return Promise.reject(new Error("Server error on flow save"))
                    } else {
                        commit("setFlow", response.data)

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
            return this.$http.post("/api/v1/flows", options.flow).then(response => {
                commit("setFlow", response.data)

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

                return response.data;
            })
        },
        loadRevisions({commit}, options) {
            return this.$http.get(`/api/v1/flows/${options.namespace}/${options.id}/revisions`).then(response => {
                commit("setRevisions", response.data)

                return response.data;
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
            state.flowGraph = undefined
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

            if (trigger.backfill  === undefined) {
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
        }
    },
    getters: {
        flow (state) {
            if (state.flow) {
                return state.flow;
            }
        }
    }
}
