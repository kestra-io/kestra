import Vue from 'vue'
export default {
    namespaced: true,
    state: {
        namespaces: undefined,
        namespace: undefined
    },

    actions: {
        loadNamespaces({ commit }, options) {
            return Vue.axios.get(`/api/v1/namespaces`, { params: options }).then(response => {
                commit('setNamespaces', response.data)
            })
        },
    },
    mutations: {
        setNamespaces(state, namespaces) {
            state.namespaces = namespaces
        },
        setNamespace(state, namespace) {
            state.namespace = namespace
        }
    },
    getters: {}
}