export default {
    namespaced: true,
    state: {
        plugin: undefined,
        plugins: undefined,
        icons: undefined
    },
    actions: {
        list({commit}) {
            return this.$http.get("/api/v1/plugins", {}).then(response => {
                commit("setPlugins", response.data)

                return response.data;
            })
        },
        load({commit}, options) {
            return this.$http.get(`/api/v1/plugins/${options.cls}`, {}).then(response => {
                commit("setPlugin", response.data)

                return response.data;
            })
        },
        icons({commit}) {
            return this.$http.get("/api/v1/plugins/icons", {}).then(response => {
                commit("setIcons", response.data)

                return response.data;
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
        setIcons(state, icons) {
            state.icons = icons
        },
    },
    getters: {}
}

