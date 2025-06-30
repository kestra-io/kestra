import {defineStore} from "pinia";
import {apiUrlWithoutTenants} from "override/utils/route";
import * as YamlUtils from "@kestra-io/ui-libs/flow-yaml-utils";
import semver from "semver";
import {useApiStore} from "./api";
import {Schemas} from "../components/code/utils/types";

interface PluginComponent {
    icon?: string;
    cls?: string;
    description?: string;
    properties?: Record<string, any>;
    schema: Schemas;
}

interface Plugin {
    tasks: PluginComponent[];
    triggers: PluginComponent[];
    conditions: PluginComponent[];
    controllers: PluginComponent[];
    storages: PluginComponent[];
    taskRunners: PluginComponent[];
    charts: PluginComponent[];
    dataFilters: PluginComponent[];
    aliases: PluginComponent[];
    logExporters: PluginComponent[];
}

interface State {
    plugin: PluginComponent | undefined;
    versions: string[] | undefined;
    pluginAllProps: any | undefined;
    plugins: Plugin[] | undefined;
    pluginSingleList: PluginComponent[] | undefined;
    icons: Record<string, string> | undefined;
    pluginsDocumentation: Record<string, PluginComponent>;
    editorPlugin: (PluginComponent & { cls: string }) | undefined;
    inputSchema: any | undefined;
    inputsType: any | undefined;
}

interface LoadOptions {
    cls: string;
    version?: string;
    all?: boolean;
    commit?: boolean;
}

export const usePluginsStore = defineStore("plugins", {
    state: (): State => ({
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
    }),

    getters: {
        getPluginSingleList: (state): PluginComponent[] | undefined => state.pluginSingleList,
        getPluginsDocumentation: (state): Record<string, PluginComponent> => state.pluginsDocumentation,
        getIcons: (state): Record<string, string> | undefined => state.icons
    },

    actions: {
        async list() {
            const response = await this.$http.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins`);
            this.plugins = response.data;
            this.pluginSingleList = response.data
                .map(plugin => plugin.tasks
                    .concat(plugin.triggers, plugin.conditions, plugin.controllers,
                           plugin.storages, plugin.taskRunners, plugin.charts,
                           plugin.dataFilters, plugin.aliases, plugin.logExporters))
                .flat();
            return response.data;
        },

        async listWithSubgroup(options: Record<string, any>) {
            const response = await this.$http.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins/groups/subgroups`, {
                params: options
            });
            this.plugins = response.data;
            this.pluginSingleList = response.data
                .map(plugin => plugin.tasks
                    .concat(plugin.triggers, plugin.conditions, plugin.controllers,
                           plugin.storages, plugin.taskRunners, plugin.charts,
                           plugin.dataFilters, plugin.aliases, plugin.logExporters))
                .flat();
            return response.data;
        },

        async load(options: LoadOptions) {
            if (options.cls === undefined) {
                throw new Error("missing required cls");
            }

            const id = options.version ? `${options.cls}/${options.version}` : options.cls;
            const cachedPluginDoc = this.pluginsDocumentation[id];
            if (!options.all && cachedPluginDoc) {
                this.plugin = cachedPluginDoc;
                return cachedPluginDoc;
            }

            const url = options.version ?
                `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions/${options.version}` :
                `${apiUrlWithoutTenants()}/plugins/${options.cls}`;

            const response = await this.$http.get<PluginComponent>(url, {params: options});

            if (options.commit !== false) {
                if (options.all === true) {
                    this.pluginAllProps = response.data;
                } else {
                    this.plugin = response.data;
                }
            }

            if (!options.all) {
                this.pluginsDocumentation = {
                    ...this.pluginsDocumentation,
                    [id]: response.data
                };
            }

            return response.data;
        },

        loadVersions(options: { cls: string; commit?: boolean }) {
            const promise = this.$http.get(
                `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions`
            );
            return promise.then(response => {
                if (options.commit !== false) {
                    this.versions = response.data.versions;
                }
                return response.data;
            });
        },

        fetchIcons() {
            const apiStore = useApiStore();
            return Promise.all([
                this.$http.get(`${apiUrlWithoutTenants()}/plugins/icons`, {}),
                apiStore.pluginIcons()
            ]).then(responses => {
                const icons = responses[0].data;

                for (const [key, plugin] of Object.entries(responses[1].data)) {
                    if (icons[key] === undefined) {
                        icons[key] = plugin;
                    }
                }

                this.icons = icons;

                return icons;
            });
        },

        groupIcons() {
            return Promise.all([
                this.$http.get(`${apiUrlWithoutTenants()}/plugins/icons/groups`, {})
            ]).then(responses => {
                return responses[0].data;
            });
        },

        loadInputsType() {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/inputs`, {}).then(response => {
                this.inputsType = response.data;

                return response.data;
            });
        },
        loadInputSchema(options: {type: string}) {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/inputs/${options.type}`, {}).then(response => {
                this.inputSchema = response.data;

                return response.data;
            });
        },
        loadSchemaType(options: {type: string} = {type: "flow"}) {
            return this.$http.get(`${apiUrlWithoutTenants()}/plugins/schemas/${options.type}`, {}).then(response => {
                return response.data;
            });
        },


        async updateDocumentation(options: { task?: string; event?: { model: { getValue: () => string }, position: any } }) {
            const taskType = options.event ? (options.task !== undefined ? options.task : YamlUtils.getTypeAtPosition(
                options.event.model.getValue(),
                options.event.position,
                this.getPluginSingleList
            )) : options.task;

            const taskVersion: string | undefined = options.event
                ? YamlUtils.getVersionAtPosition(
                    options?.event?.model?.getValue(),
                    options?.event?.position
                )
                : undefined;

            if (taskType) {
                let payload:LoadOptions = {cls: taskType};
                if (taskVersion !== undefined) {
                    // Check if the version is valid to avoid error
                    // when loading plugin
                    if (semver.valid(taskVersion) !== null ||
                        "latest" === taskVersion.toString().toLowerCase() ||
                        "oldest" === taskVersion.toString().toLowerCase()
                    ) {
                        payload = {
                            ...payload,
                            version: taskVersion
                        };
                    }
                }
                this.load(payload).then((plugin) => {
                    this.editorPlugin = {
                        cls: taskType,
                        ...plugin,
                    };
                });
            } else {
                this.editorPlugin = undefined;
            }
        }
    },

});
