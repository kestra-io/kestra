import Vue from "vue"
export default {
    namespaced: true,
    state: {
        version: undefined,
    },

    actions: {
        loadVersion({commit}) {
            return Vue.axios.get("/api/v1/version").then(response => {
                commit("setVersion", response.data)

                return response.data;
            })
        },
    },
    mutations: {
        setVersion(state, version) {
            state.version = version
        }
    },
    getters: {}
}
