import {defineStore} from "pinia"
import {ref, computed, toRaw} from "vue";
import {trackPluginDocumentationView} from "../utils/tabTracking";
import {apiUrlWithoutTenants} from "override/utils/route";
import semver from "semver";
import {useApiStore} from "./api";
import {Schemas} from "../components/no-code/utils/types";
import InitialFlowSchema from "./flow-schema.json"
import {isEntryAPluginElementPredicate} from "@kestra-io/ui-libs";
import {useAxios} from "../utils/axios";

export interface PluginComponent {
    icon?: string;
    cls?: string;
    deprecated?: boolean;
    version?: string;
    description?: string;
    properties?: Record<string, any>;
    schema: Schemas;
    markdown?: string;
}

export interface Plugin {
    tasks: PluginComponent[];
    triggers: PluginComponent[];
    conditions: PluginComponent[];
    controllers: PluginComponent[];
    storages: PluginComponent[];
    taskRunners: PluginComponent[];
    charts: PluginComponent[];
    dataFilters: PluginComponent[];
    dataFiltersKPI: PluginComponent[];
    aliases: PluginComponent[];
    logExporters: PluginComponent[];
    apps: PluginComponent[];
    appBlocks: PluginComponent[];
    additionalPlugins: PluginComponent[];
}

interface LoadOptions {
    cls: string;
    version?: string;
    all?: boolean;
    commit?: boolean;
    hash?: number;
}

interface JsonSchemaDef {
    $ref?: string,
    allOf?: JsonSchemaDef[],
    type?: string,
    properties?: Record<string, any>,
}

export function removeRefPrefix(ref?: string): string {
    return ref?.replace(/^#\/definitions\//, "") ?? "";
}

export const usePluginsStore = defineStore("plugins", () => {
    const plugin = ref<PluginComponent>();
    const versions = ref<string[]>();
    const pluginAllProps = ref<any>();
    const plugins = ref<Plugin[]>();
    const apiIcons = ref<Record<string, string>>({});
    const pluginsIcons = ref<Record<string, string>>({});
    const icons = computed(() => {
        return {
            ...pluginsIcons.value,
            ...apiIcons.value
        }
    })
    const pluginsDocumentation = ref<Record<string, PluginComponent>>({});
    const editorPlugin = ref<(PluginComponent & {cls: string})>();
    const inputSchema = ref<any>();
    const inputsType = ref<any>();
    const schemaType = ref<Record<string, any>>();
    const currentlyLoading = ref<{type?: string; version?: string}>();
    const forceIncludeProperties = ref<string[]>();
    const _iconsPromise = ref<Promise<Record<string, string>>>();

    const axios = useAxios();

    const flowSchema = computed(() => {
        return schemaType.value?.flow ?? InitialFlowSchema;
    });
    const flowDefinitions = computed(() => {
        return flowSchema.value.definitions;
    });
    const flowRootSchema = computed(() => {
        return flowDefinitions.value?.[removeRefPrefix(flowSchema.value.$ref)];
    });
    const flowRootProperties = computed(() => {
        return flowRootSchema.value?.properties;
    });
    const allTypes = computed(() => {
        return plugins.value?.flatMap(plugin => Object.entries(plugin))
            ?.filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            ?.flatMap(([, value]: [string, PluginComponent[]]) => value.map(({cls}) => cls!)) ?? [];
    });
    const deprecatedTypes = computed(() => {
        return plugins.value?.flatMap(plugin => Object.entries(plugin))
            ?.filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            ?.flatMap(([, value]: [string, PluginComponent[]]) => value.filter(({deprecated}) => deprecated === true).map(({cls}) => cls!)) ?? [];
    });

    function resolveRef(obj: JsonSchemaDef): JsonSchemaDef {
        if (obj?.$ref) {
            return flowDefinitions.value?.[removeRefPrefix(obj.$ref)];
        }
        if (obj?.allOf) {
            const def = obj.allOf.reduce((acc: any, item) => {
                if (item.$ref) {
                    const ref = toRaw(flowDefinitions.value?.[removeRefPrefix(item.$ref)]);
                    if (ref?.type === "object" && ref?.properties) {
                        acc.properties = {
                            ...acc.properties,
                            ...ref.properties
                        };
                    }
                }
                if (item.type === "object" && item.properties) {
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
    }

    async function filteredPlugins(excludedElements: string[]) {
        if (plugins.value === undefined) {
            plugins.value = await listWithSubgroup({includeDeprecated: false});
        }

        return plugins.value.map(p => ({
            ...p,
            ...Object.fromEntries(excludedElements.map(e => [e, undefined]))
        })).filter(p => Object.entries(p)
                .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                .some(([, value]: [string, PluginComponent[]]) => value.length !== 0))
    }

    async function list() {
        const response = await axios.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins`);
        plugins.value = response.data;
        return response.data;
    }

    async function listWithSubgroup(options: Record<string, any>) {
        const response = await axios.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins/groups/subgroups`, {
            params: options
        });
        plugins.value = response.data;
        return response.data;
    }

    async function load(options: LoadOptions) {
        if (options.cls === undefined) {
            throw new Error("missing required cls");
        }

        const id = options.version ? `${options.cls}/${options.version}` : options.cls;
        const cachedPluginDoc = pluginsDocumentation.value[options.hash ? options.hash + id : id];
        if (!options.all && cachedPluginDoc) {
            plugin.value = cachedPluginDoc;
            return cachedPluginDoc;
        }

        const baseUrl = options.version ?
            `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions/${options.version}` :
            `${apiUrlWithoutTenants()}/plugins/${options.cls}`;

        const url = options.hash ? `${baseUrl}?hash=${options.hash}` : baseUrl;

        const response = await axios.get<PluginComponent>(url);

        if (options.commit !== false) {
            if (options.all === true) {
                pluginAllProps.value = response.data;
            } else {
                plugin.value = response.data;
            }
        }

        if (!options.all) {
            pluginsDocumentation.value = {
                ...pluginsDocumentation.value,
                [options.hash ? options.hash+id : id]: response.data
            };
        }

        return response.data;
    }

    async function loadVersions(options: {cls: string; commit?: boolean}): Promise<{type: string, versions: string[]}> {
        const response = await axios.get(
            `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions`
        );
        if (options.commit !== false) {
            versions.value = response.data.versions;
        }

        return response.data;
    }

    const iconsLoaded = ref(false)

    function fetchIcons() {
        if (iconsLoaded.value) {
            return Promise.resolve(icons.value);
        }

        if (_iconsPromise.value) {
            return _iconsPromise.value;
        }

        const apiStore = useApiStore();

        const apiPromise = apiStore.pluginIcons().then(response => {
            apiIcons.value = response.data ?? {};
            return response.data;
        });

        const iconsPromise =
            axios.get(`${apiUrlWithoutTenants()}/plugins/icons`, {}).then(response => {
                pluginsIcons.value = response.data ?? {};
                return pluginsIcons.value;
            });

        _iconsPromise.value = Promise.all([apiPromise, iconsPromise]).then(() => {
            iconsLoaded.value = true;
            return icons.value;
        })

        return _iconsPromise.value;
    }

    function groupIcons() {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/icons/groups`, {})
        .then(response => {
            return response.data;
        });
    }

    function loadInputsType() {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/inputs`, {}).then(response => {
            inputsType.value = response.data;
            return response.data;
        });
    }

    function loadInputSchema(options: {type: string}) {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/inputs/${options.type}`, {}).then(response => {
            inputSchema.value = response.data;
            return response.data;
        });
    }

    function lazyLoadSchemaType(options: {type: string}) {
        if(schemaType.value?.[options.type]) {
            return Promise.resolve(schemaType.value[options.type]);
        }

        return loadSchemaType(options);
    }

    function loadSchemaType(options: {type: string}) {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/schemas/${options.type}`, {}).then(response => {
            schemaType.value = schemaType.value || {};
            schemaType.value[options.type] = response.data;
            return response.data;
        });
    }

    async function updateDocumentation(pluginElement?: ({type: string, version?: string, forceRefresh?: boolean} & Record<string, any>) | undefined) {
        if (!pluginElement?.type || !allTypes.value.includes(pluginElement.type)) {
            editorPlugin.value = undefined;
            currentlyLoading.value = undefined;
            return;
        }

        const {type, version, forceRefresh = false} = pluginElement;

        if (currentlyLoading.value?.type === type &&
            currentlyLoading.value?.version === version &&
            !forceRefresh) {
            return
        }

        if (!forceRefresh &&
            editorPlugin.value?.cls === type &&
            editorPlugin.value?.version === version) {
            return;
        }

        let payload: LoadOptions = {cls: type, hash: pluginElement.hash};

        if (version !== undefined) {
            if (semver.valid(version) !== null ||
                "latest" === version.toString().toLowerCase() ||
                "oldest" === version.toString().toLowerCase()
            ) {
                payload = {
                    ...payload,
                    version
                };
            }
        }

        currentlyLoading.value = {
            type,
            version,
        };

        load(payload).then((pluginData) => {
            editorPlugin.value = {
                cls: type,
                version,
                ...pluginData,
            };

            trackPluginDocumentationView(type);

            forceIncludeProperties.value = Object.keys(pluginElement).filter(k => k !== "type" && k !== "version" && k !== "forceRefresh");
        });
    }

    return {
        // state
        plugin,
        versions,
        pluginAllProps,
        plugins,
        icons,
        pluginsDocumentation,
        editorPlugin,
        inputSchema,
        inputsType,
        schemaType,
        currentlyLoading,
        forceIncludeProperties,
        _iconsPromise,
        // getters
        flowSchema,
        flowDefinitions,
        flowRootSchema,
        flowRootProperties,
        allTypes,
        deprecatedTypes,
        // actions
        resolveRef,
        filteredPlugins,
        list,
        listWithSubgroup,
        load,
        loadVersions,
        fetchIcons,
        groupIcons,
        loadInputsType,
        loadInputSchema,
        loadSchemaType,
        lazyLoadSchemaType,
        updateDocumentation,
    };
});
