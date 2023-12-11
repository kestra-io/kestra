import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        namespaces: undefined,
    },

    actions: {
        loadNamespaces({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/${options.dataType}s/distinct-namespaces`).then(response => {
                commit("setNamespaces", response.data)
            })
        },
    },
    mutations: {
        setNamespaces(state, namespaces) {
            state.namespaces = namespaces
        }
    },
    getters: {}
}
