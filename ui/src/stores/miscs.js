import Vue from "vue"
export default {
    namespaced: true,
    state: {
        configs: undefined,
    },

    actions: {
        loadConfigs({commit}) {
            return Vue.axios.get("/api/v1/configs").then(response => {
                commit("setConfigs", response.data)

                return response.data;
            })
        },
    },
    mutations: {
        setConfigs(state, configs) {
            state.configs = configs
        }
    },
    getters: {}
}
