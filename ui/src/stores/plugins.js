import {apiUrl, apiUrlWithoutTenants} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        plugin: undefined,
        versions: undefined,
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
            return this.$http.get(`${apiUrl(this)}/plugins`, {}).then(response => {
                commit("setPlugins", response.data)
                commit("setPluginSingleList", response.data.map(plugin => plugin.tasks.concat(plugin.triggers, plugin.conditions, plugin.controllers, plugin.storages, plugin.taskRunners, plugin.charts, plugin.dataFilters, plugin.aliases, plugin.logExporters)).flat())
                return response.data;
            })
        },
        listWithSubgroup({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/plugins/groups/subgroups`, {
                params: options
            }).then(response => {
                commit("setPlugins", response.data)
                commit("setPluginSingleList", response.data.map(plugin => plugin.tasks.concat(plugin.triggers, plugin.conditions, plugin.controllers, plugin.storages, plugin.taskRunners, plugin.charts, plugin.dataFilters, plugin.aliases, plugin.logExporters)).flat())
                return response.data;
            })
        },
        load({commit, state}, options) {
            if (options.cls === undefined) {
                throw new Error("missing required cls");
            }

            const id = options.version ? `${options.cls}/${options.version}` : options.cls;
            const cachedPluginDoc = state.pluginsDocumentation[id];
            if (!options.all && cachedPluginDoc) {
                commit("setPlugin", cachedPluginDoc);
                return Promise.resolve(cachedPluginDoc);
            }

            const url = options.version ?
                `${apiUrl(this)}/plugins/${options.cls}/versions/${options.version}` :
                `${apiUrl(this)}/plugins/${options.cls}`;

            return this.$http.get(url, {params: options}).then(response => {
                if (options.commit !== false) {
                    if (options.all === true) {
                        commit("setPluginAllProps", response.data);
                    } else {
                        commit("setPlugin", response.data);
                    }
                }

                if (!options.all) {
                    commit("addPluginDocumentation", {[id]: response.data});
                }

                return response.data;
            })
        },
        loadVersions({commit}, options) {
            const promise = this.$http.get(
                `${apiUrl(this)}/plugins/${options.cls}/versions`
            );
            return promise.then(response => {
                if (options.commit !== false) {
                    commit("setVersions", response.data.versions);
                }
                return response.data;
            })
        },
        icons({commit}) {
            return Promise.all([
                this.$http.get(`${apiUrl(this)}/plugins/icons`, {}),
                this.dispatch("api/pluginIcons")
            ]).then(responses => {
                const icons = responses[0].data;

                for (const [key, plugin] of Object.entries(responses[1].data)) {
                    if (icons[key] === undefined) {
                        icons[key] = plugin
                    }
                }

                commit("setIcons", icons);

                return icons;
            });
        },
        groupIcons(_) {
            return Promise.all([
                this.$http.get(`${apiUrl(this)}/plugins/icons/groups`, {}),
            ]).then(responses => {
                return responses[0].data
            });
        },
        loadInputsType({commit}) {
            return this.$http.get(`${apiUrl(this)}/plugins/inputs`, {}).then(response => {
                commit("setInputsType", response.data)

                return response.data;
            })
        },
        loadInputSchema({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/plugins/inputs/${options.type}`, {}).then(response => {
                commit("setInputSchema", response.data)

                return response.data;
            })
        },
        loadSchemaType(_, options = {type: "flow"}) {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/schemas/${options.type}`, {}).then(response => {
                return response.data;
            })
        }

    },
    mutations: {
        setPlugin(state, plugin) {
            state.plugin = plugin
        },
        setVersions(state, versions) {
            state.versions = versions
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
        addPluginDocumentation(state, pluginDocumentation) {
            state.pluginsDocumentation = {...state.pluginsDocumentation, ...pluginDocumentation}
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
        getIcons: state => state.icons
    }
}

