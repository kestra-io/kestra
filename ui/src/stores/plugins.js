export default {
    namespaced: true,
    state: {
        plugin: undefined,
        pluginAllProps: undefined,
        plugins: undefined,
        pluginSingleList: undefined,
        icons: undefined,
        pluginsDocumentation: {},
        editorPlugin: undefined,
        inputSchema: undefined,
        inputsType: undefined
    },
    actions: {
        list({commit}) {
            return this.$http.get("/api/v1/plugins", {}).then(response => {
                commit("setPlugins", response.data)
                commit("setPluginSingleList", response.data.map(plugin => plugin.tasks.concat(plugin.triggers, plugin.conditions, plugin.controllers, plugin.storages)).flat())
                return response.data;
            })
        },
        load({commit}, options) {
            if (options.cls === undefined) {
                throw new Error("missing required cls");
            }

            return this.$http.get(`/api/v1/plugins/${options.cls}`, {params: options}).then(response => {
                if(options.all === true) {
                    commit("setPluginAllProps", response.data)
                } else {
                    commit("setPlugin", response.data)
                }
                return response.data;
            })
        },
        icons({commit}) {
            return this.$http.get("/api/v1/plugins/icons", {}).then(response => {
                commit("setIcons", response.data)

                return response.data;
            })
        },
        loadInputsType({commit}) {
            return this.$http.get(`/api/v1/plugins/inputs`, {}).then(response => {
                commit("setInputsType", response.data)

                return response.data;
            })
        },
        loadInputSchema({commit}, options) {
            return this.$http.get(`/api/v1/plugins/inputs/${options.type}`, {}).then(response => {
                commit("setInputSchema", response.data)

                return response.data;
            })
        }

    },
    mutations: {
        setPlugin(state, plugin) {
            state.plugin = plugin
        },
        setPluginAllProps(state, pluginAllProps) {
            state.pluginAllProps = pluginAllProps
        },
        setPlugins(state, plugins) {
            state.plugins = plugins
        },
        setPluginSingleList(state, pluginSingleList) {
            state.pluginSingleList = pluginSingleList
        },
        setIcons(state, icons) {
            state.icons = icons
        },
        setPluginsDocumentation(state, pluginsDocumentation) {
            state.pluginsDocumentation = pluginsDocumentation
        },
        setEditorPlugin(state, editorPlugin) {
            state.editorPlugin = editorPlugin
        },
        setInputsType(state, inputsType) {
            state.inputsType = inputsType
        },
        setInputSchema(state, schema) {
            state.inputSchema = schema
        }
    },
    getters: {
        getPluginSingleList: state => state.pluginSingleList,
        getPluginsDocumentation: state => state.pluginsDocumentation,
    }
}

