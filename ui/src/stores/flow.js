import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        flows: undefined,
        flow: undefined,
        total: 0,
        dataTree: undefined,
    },

    actions: {
        findFlows({ commit }, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ''
            delete options.sort
            return Vue.axios.get(`/api/v1/flows/search${sortString}`, {
                params: options
            }).then(response => {
                commit('setFlows', response.data.results)
                commit('setTotal', response.data.total)

                return response.data;
            })
        },
        loadFlow({ commit }, options) {
            return Vue.axios.get(`/api/v1/flows/${options.namespace}/${options.id}`).then(response => {
                commit('setFlow', response.data)
            })
        },
        saveFlow({ commit }, options) {
            return Vue.axios.put(`/api/v1/flows/${options.flow.namespace}/${options.flow.id}`, options.flow).then(response => {
                if (response.status >= 300) {
                    return Promise.reject(new Error("Server error on flow save"))
                } else {
                    commit('setFlow', response.data)
                }
            })
        },
        createFlow({ commit }, options) {
            return Vue.axios.post('/api/v1/flows', options.flow).then(response => {
                commit('setFlow', response.data.flow)
            })
        },
        deleteFlow({ commit }, flow) {
            return Vue.axios.delete(`/api/v1/flows/${flow.namespace}/${flow.id}`).then(() => {
                commit('setFlow', null)
            })
        },
        loadTree({ commit }, flow) {
            return Vue.axios.get(`/api/v1/flows/${flow.namespace}/${flow.id}/tree`).then(response => {
                commit('setDataTree', response.data.tasks)
            })
        }
    },
    mutations: {
        setFlows(state, flows) {
            state.flows = flows
        },
        setFlow(state, flow) {
            if (!flow) {
                state.flow = flow;
            } else {
                if (flow.triggers !== undefined) {
                    flow.triggers = flow.triggers.map(trigger => {
                        if (trigger.backfill  === undefined) {
                            trigger.backfill = {
                                start: undefined
                            }
                        }

                        return trigger;
                    })
                }

                state.flow = {...flow}
            }
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
        setDataTree(state, dataTree) {
            state.dataTree = dataTree
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
