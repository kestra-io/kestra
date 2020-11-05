import Vue from "vue"

export default {
    namespaced: true,
    state: {
        plugin: undefined,
        plugins: undefined,
    },
    actions: {
        list({commit}) {
            return Vue.axios.get("/api/v1/plugins").then(response => {
                commit("setPlugins", response.data)
            })
        },
        load({commit}, options) {
            return Vue.axios.get(`/api/v1/plugins/${options.cls}`).then(response => {
                commit("setPlugin", response.data)
            })
        },

    },
    mutations: {
        setPlugin(state, plugin) {
            state.plugin = plugin
        },
        setPlugins(state, plugins) {
            state.plugins = plugins
        },
    },
    getters: {}
}

