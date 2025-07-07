import {defineStore} from "pinia";
import {apiUrlWithoutTenants} from "override/utils/route";
import * as YamlUtils from "@kestra-io/ui-libs/flow-yaml-utils";
import semver from "semver";
import {useApiStore} from "./api";
import {Schemas} from "../components/code/utils/types";
import InitialFlowSchema from "./flow-schema.json"
import {toRaw} from "vue";

interface PluginComponent {
    icon?: string;
    cls?: string;
    version?: string;
    description?: string;
    properties?: Record<string, any>;
    schema: Schemas;
    markdown?: string;
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
    plugin?: PluginComponent;
    versions?: string[];
    pluginAllProps?: any;
    plugins?: Plugin[];
    pluginSingleList?: PluginComponent[];
    icons?: Record<string, string> ;
    pluginsDocumentation: Record<string, PluginComponent>;
    editorPlugin?: (PluginComponent & { cls: string });
    inputSchema?: any;
    inputsType?: any;
    schemaType?: Record<string, any>;
    currentlyLoading?: {
        taskType?: string;
        taskVersion?: string;
    };
    _iconsPromise: Promise<Record<string, string>> | undefined;
}

interface LoadOptions {
    cls: string;
    version?: string;
    all?: boolean;
    commit?: boolean;
}

interface JsonSchemaDef {
            $ref?:string,
            allOf?: JsonSchemaDef[],
            type?: string,
            properties?: Record<string, any>,
}

export function removeRefPrefix(ref?: string): string {
    return ref?.replace(/^#\/definitions\//, "") ?? "";
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
        inputsType: undefined,
        schemaType: undefined,
        _iconsPromise: undefined
    }),

    getters: {
        flowSchema (state): {
            definitions: any,
            $ref: string,
        } {
            return state.schemaType?.flow ?? InitialFlowSchema;
        },
        flowDefinitions (): Record<string, any> | undefined {
            return this.flowSchema.definitions;
        },
        flowRootSchema (): Record<string, any> | undefined {
            return this.flowDefinitions?.[removeRefPrefix(this.flowSchema.$ref)];
        },
        flowRootProperties (): Record<string, any> | undefined {
            return this.flowRootSchema?.properties;
        }
    },

    actions: {
        resolveRef(obj: JsonSchemaDef): JsonSchemaDef {
            if(obj?.$ref){
                return this.flowDefinitions?.[removeRefPrefix(obj.$ref)];
            }
            if (obj?.allOf) {
                const def = obj.allOf.reduce((acc: any, item) => {
                    if (item.$ref) {
                        const ref = toRaw(this.flowDefinitions?.[removeRefPrefix(item.$ref)]);
                        if (ref?.type === "object" && ref?.properties) {
                            acc.properties = {
                                ...acc.properties,
                                ...ref.properties
                            };
                        }
                    }
                    if(item.type === "object" && item.properties) {
                        acc.properties = {
                            ...acc.properties,
                            ...item.properties
                        };
                    }
                    return acc;
                }, {});
                return def
            }
            return obj;
        },
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
            if(this.icons){
                return Promise.resolve(this.icons);
            }

            if (this._iconsPromise) {
                return this._iconsPromise;
            }

            const apiStore = useApiStore();

            const apiPromise = apiStore.pluginIcons().then(response => {
                this.icons = response.data ?? {};
                for (const [key, plugin] of Object.entries(response.data)) {
                    if (this.icons && this.icons[key] === undefined) {
                        this.icons[key] = plugin as string;
                    }
                }
            });

            const iconsPromise =
                this.$http.get(`${apiUrlWithoutTenants()}/plugins/icons`, {}).then(response => {
                    const icons = response.data ?? {};
                    this.icons = this.icons ? {
                        ...icons,
                        ...this.icons
                    } : icons;
                });

            this._iconsPromise = Promise.all([apiPromise, iconsPromise]).then(() => {
                return this.icons ?? {};
            })

            return this._iconsPromise;
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
                this.schemaType = this.schemaType || {};
                this.schemaType[options.type] = response.data;
                return response.data;
            });
        },


        async updateDocumentation(options: { task?: string; event?: { model: { getValue: () => string }, position: any } }) {
            const taskType = options.event ? (options.task !== undefined ? options.task : YamlUtils.getTypeAtPosition(
                options.event.model.getValue(),
                options.event.position,
                this.pluginSingleList
            )) : options.task;

            const taskVersion: string | undefined = options.event
                ? YamlUtils.getVersionAtPosition(
                    options?.event?.model?.getValue(),
                    options?.event?.position
                )
                : undefined;

            // Avoid rerunning the same request twice in a row
            if(this.currentlyLoading?.taskType === taskType &&
                this.currentlyLoading?.taskVersion === taskVersion) {
                    return
            }

            // No need to reload if the plugin has not changed
            if(this.editorPlugin?.cls === taskType &&
                this.editorPlugin?.version === taskVersion) {
                    return;
            }

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

                this.currentlyLoading = {
                    taskType: taskType,
                    taskVersion: taskVersion,
                };

                this.load(payload).then((plugin) => {
                    this.editorPlugin = {
                        cls: taskType,
                        version: taskVersion,
                        ...plugin,
                    };
                });
            } else {
                this.editorPlugin = undefined;
            }
        }
    },

});
