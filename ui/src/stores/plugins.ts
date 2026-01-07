import {defineStore} from "pinia"
import {ref, computed, toRaw, nextTick} from "vue";
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
    schema: {
        properties: Schemas;
        outputs: Schemas;
    };
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

function usePluginsIcons() {
    const apiStore = useApiStore();

    const iconsLoaded = ref(false)

    const apiIcons = ref<Record<string, string>>({});
    const pluginsIcons = ref<Record<string, string>>({});
    const _iconsPromise = ref<Promise<Record<string, string>>>();
    const axios = useAxios();

    const icons = computed(() => {
        return {
            ...pluginsIcons.value,
            ...apiIcons.value
        }
    })

    function fetchIcons() {
        if (iconsLoaded.value) {
            return Promise.resolve(icons.value);
        }

        if (_iconsPromise.value) {
            return _iconsPromise.value;
        }

        const apiPromise = apiStore.pluginIcons().then(async response => {
            apiIcons.value = response.data ?? {};
            return response.data;
        });

        const iconsPromise =
            axios.get(`${apiUrlWithoutTenants()}/plugins/icons`, {}).then(async response => {
                pluginsIcons.value = response.data ?? {};
                return pluginsIcons.value;
            });

        _iconsPromise.value = Promise.all([apiPromise, iconsPromise]).then(async () => {
            iconsLoaded.value = true;
            return icons.value;
        })

        return _iconsPromise.value;
    }

    return {
        icons,
        iconsLoaded,
        fetchIcons,
    }
}

export const usePluginsStore = defineStore("plugins", () => {
    const plugin = ref<PluginComponent>();
    const versions = ref<string[]>();
    const pluginAllProps = ref<any>();
    const plugins = ref<Plugin[]>();


    const pluginsDocumentation = ref<Record<string, PluginComponent>>({});
    const editorPlugin = ref<(PluginComponent & {cls: string})>();
    const inputSchema = ref<any>();
    const inputsType = ref<any>();
    const schemaType = ref<Record<string, any>>();
    const forceIncludeProperties = ref<string[]>();

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
        const cacheKey = options.hash ? options.hash + id : id;
        const cachedPluginDoc = pluginsDocumentation.value[cacheKey];
        if (!options.all && cachedPluginDoc) {
            nextTick(() => {
                plugin.value = cachedPluginDoc;
            })
            return cachedPluginDoc;
        }

        const url = options.version ?
            `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions/${options.version}` :
            `${apiUrlWithoutTenants()}/plugins/${options.cls}`;

        const response = await axios.get<PluginComponent>(url, options.all ? {
            params: {
                all: options.all,
                hash: options.hash,
            }
        } : {});

        if (options.commit !== false) {
            if (options.all === true) {
                pluginAllProps.value = response.data;
            } else {
                plugin.value = response.data;
            }
        }

        if (!options.all) {
            pluginsDocumentation.value[cacheKey] = response.data;
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

    let currentlyLoading: {cls?: string; version?: string} | undefined = undefined;

    async function updateDocumentation(pluginElement?: (LoadOptions & {forceRefresh?: boolean}) | undefined) {
        if (!pluginElement?.cls || !allTypes.value.includes(pluginElement.cls)) {
            editorPlugin.value = undefined;
            currentlyLoading = undefined;
            return;
        }

        const {cls,  version, hash, forceRefresh = false} = pluginElement;

        if (currentlyLoading?.cls === cls &&
            currentlyLoading?.version === version &&
            !forceRefresh) {
            return
        }

        if (!forceRefresh &&
            editorPlugin.value?.cls === cls &&
            editorPlugin.value?.version === version) {
            return;
        }

        let payload: LoadOptions = {cls, version, hash}

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

        currentlyLoading = {
            cls,
            version,
        };

        const pluginData = await load(payload);

        editorPlugin.value = {
            cls,
            version,
            ...pluginData,
        };

        trackPluginDocumentationView(cls);

        forceIncludeProperties.value = Object.keys(pluginElement).filter(k => k !== "cls" && k !== "version" && k !== "forceRefresh");
    }

    const {icons, iconsLoaded, fetchIcons} = usePluginsIcons()

    function groupIcons() {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/icons/groups`, {})
        .then(response => {
            return response.data;
        });
    }

    return {
        // state
        plugin,
        versions,
        pluginAllProps,
        plugins,
        pluginsDocumentation,
        editorPlugin,
        inputSchema,
        inputsType,
        schemaType,
        currentlyLoading,
        forceIncludeProperties,

        flowSchema,
        flowDefinitions,
        flowRootSchema,
        flowRootProperties,
        allTypes,
        deprecatedTypes,

        resolveRef,
        filteredPlugins,
        list,
        listWithSubgroup,
        load,
        loadVersions,
        loadInputsType,
        loadInputSchema,
        loadSchemaType,
        lazyLoadSchemaType,
        updateDocumentation,

        // icons
        icons,
        iconsLoaded,
        fetchIcons,
        groupIcons,
    };
});
