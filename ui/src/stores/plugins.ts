import {defineStore} from "pinia"
import {ref, computed, toRaw, nextTick} from "vue"
import {trackPluginDocumentationView} from "../utils/tabTracking"
import {apiUrlWithoutTenants} from "override/utils/route"
import semver from "semver"
import {useApiStore} from "./api"
import InitialFlowSchema from "./flow-schema.json"
import {isEntryAPluginElementPredicate, type Plugin, type PluginElement} from "../utils/pluginUtils"
import type {JSONSchema} from "../components/plugins/schema/utils/schemaUtils"
import {useClient} from "@kestra-io/kestra-sdk"

export interface PluginComponent {
    icon?: string;
    cls?: string;
    title?: string;
    deprecated?: boolean;
    version?: string;
    description?: string;
    properties?: Record<string, any>;
    schema: JSONSchema;
    markdown?: string;
}

export type {Plugin} from "../utils/pluginUtils"
export interface TriggerPluginDto {
    type: string;
    name: string;
    description: string | null;
    group: "core" | "realtime" | "app";
    ee: boolean;
    icon: string;
    deprecated: boolean | null;
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

export function removeRefPrefix(refStr?: string): string {
    return refStr?.replace(/^#\/definitions\//, "") ?? ""
}

interface PluginIconData {
    icon: string;
    flowable: boolean;
}

function usePluginsIcons() {
    const apiStore = useApiStore()

    const iconsLoaded = ref(false)

    const apiIcons = ref<Record<string, PluginIconData>>({})
    const pluginsIcons = ref<Record<string, PluginIconData>>({})
    const iconsPromiseLocal = ref<Promise<Record<string, PluginIconData>>>()
    const axios = useClient()

    const icons = computed(() => {
        return {
            ...pluginsIcons.value,
            ...apiIcons.value,
        }
    })

    function fetchIcons() {
        if (iconsLoaded.value) {
            return Promise.resolve(icons.value)
        }

        if (iconsPromiseLocal.value) {
            return iconsPromiseLocal.value
        }

        const apiPromise = apiStore.pluginIcons().then(async response => {
            apiIcons.value = response.data ?? {}
            return response.data
        })

        const iconsPromise =
            axios.get(`${apiUrlWithoutTenants()}/plugins/icons`, {}).then(async response => {
                pluginsIcons.value = response.data ?? {}
                return pluginsIcons.value
            })

        iconsPromiseLocal.value = Promise.all([apiPromise, iconsPromise]).then(async () => {
            iconsLoaded.value = true
            return icons.value
        })

        return iconsPromiseLocal.value
    }

    return {
        icons,
        iconsLoaded,
        fetchIcons,
    }
}

export const usePluginsStore = defineStore("plugins", () => {
    const plugin = ref<PluginComponent>()
    const versions = ref<string[]>()
    const pluginAllProps = ref<any>()
    const plugins = ref<Plugin[]>()


    const pluginsDocumentation = ref<Record<string, PluginComponent>>({})
    const editorPlugin = ref<(PluginComponent & {cls: string})>()
    const inputSchema = ref<any>()
    const inputsType = ref<any>()
    const schemaType = ref<Record<string, any>>()
    const forceIncludeProperties = ref<string[]>()

    const axios = useClient()

    const flowSchema = computed(() => {
        return schemaType.value?.flow ?? InitialFlowSchema
    })
    const flowDefinitions = computed(() => {
        return flowSchema.value.definitions
    })
    const flowRootSchema = computed(() => {
        return flowDefinitions.value?.[removeRefPrefix(flowSchema.value.$ref)]
    })
    const flowRootProperties = computed(() => {
        return flowRootSchema.value?.properties
    })
    const allTypes = computed(() => {
        return plugins.value?.flatMap(p => Object.entries(p))
            ?.filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            ?.flatMap(([, value]) => (value as PluginElement[]).map(({cls}) => cls)) ?? []
    })
    const deprecatedTypes = computed(() => {
        const deprecatedPlugins = plugins.value?.flatMap(p => Object.entries(p))
            ?.filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            ?.flatMap(([, value]) => (value as PluginElement[]).filter(({deprecated}) => deprecated === true).map(({cls}) => cls)) ?? []
        return [
            ...deprecatedPlugins,
            ...(plugins.value?.flatMap(({aliases}) => aliases ?? [])) ?? [],
        ]
    })

    function resolveRef(obj: JsonSchemaDef): JsonSchemaDef {
        if (obj?.$ref) {
            return flowDefinitions.value?.[removeRefPrefix(obj.$ref)]
        }
        if (obj?.allOf) {
            const def = obj.allOf.reduce((acc: any, item) => {
                if (item.$ref) {
                    const resolved = toRaw(flowDefinitions.value?.[removeRefPrefix(item.$ref)])
                    if (resolved?.type === "object" && resolved?.properties) {
                        acc.properties = {
                            ...acc.properties,
                            ...resolved.properties,
                        }
                    }
                }
                if (item.type === "object" && item.properties) {
                    acc.properties = {
                        ...acc.properties,
                        ...item.properties,
                    }
                }
                return acc
            }, {})
            return def
        }
        return obj
    }

    async function filteredPlugins(excludedElements: string[]) {
        if (plugins.value === undefined) {
            plugins.value = await listWithSubgroup({includeDeprecated: false})
        }

        return plugins.value.map(p => ({
            ...p,
            ...Object.fromEntries(excludedElements.map(e => [e, undefined])),
        })).filter(p => Object.entries(p)
                .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                .some(([, value]) => (value as PluginElement[]).length !== 0))
    }

    async function list() {
        const response = await axios.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins`)
        plugins.value = response.data
        return response.data
    }

    async function listTriggers() {
        const response = await axios.get<{results: TriggerPluginDto[]; total: number}>(
            `${apiUrlWithoutTenants()}/plugins/triggers`,
        )
        return response.data.results
    }

    async function listWithSubgroup(options: Record<string, any>) {
        const response = await axios.get<Plugin[]>(`${apiUrlWithoutTenants()}/plugins/groups/subgroups`, {
            params: options,
        })
        plugins.value = response.data
        return response.data
    }

    async function load(options: LoadOptions) {
        if (options.cls === undefined) {
            throw new Error("missing required cls")
        }

        const id = options.version ? `${options.cls}/${options.version}` : options.cls
        const cacheKey = options.hash ? options.hash + id : id
        const cachedPluginDoc = pluginsDocumentation.value[cacheKey]
        if (!options.all && cachedPluginDoc) {
            nextTick(() => {
                plugin.value = cachedPluginDoc
            })
            return cachedPluginDoc
        }

        const url = options.version ?
            `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions/${options.version}` :
            `${apiUrlWithoutTenants()}/plugins/${options.cls}`

        const response = await axios.get<PluginComponent>(url, options.all ? {
            params: {
                all: options.all,
                hash: options.hash,
            },
        } : {})

        if (options.commit !== false) {
            if (options.all === true) {
                pluginAllProps.value = response.data
            } else {
                plugin.value = response.data
            }
        }

        if (!options.all) {
            pluginsDocumentation.value[cacheKey] = response.data
        }

        return response.data
    }

    async function loadVersions(options: {cls: string; commit?: boolean}): Promise<{type: string, versions: string[]}> {
        const response = await axios.get(
            `${apiUrlWithoutTenants()}/plugins/${options.cls}/versions`,
        )
        if (options.commit !== false) {
            versions.value = response.data.versions
        }

        return response.data
    }

    function loadInputsType() {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/inputs`, {}).then(response => {
            inputsType.value = response.data
            return response.data
        })
    }

    function loadInputSchema(options: {type: string}) {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/inputs/${options.type}`, {}).then(response => {
            inputSchema.value = response.data
            return response.data
        })
    }

    function lazyLoadSchemaType(options: {type: string}) {
        if(schemaType.value?.[options.type]) {
            return Promise.resolve(schemaType.value[options.type])
        }

        return loadSchemaType(options)
    }

    function loadSchemaType(options: {type: string}) {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/schemas/${options.type}`, {}).then(response => {
            schemaType.value = schemaType.value || {}
            schemaType.value[options.type] = response.data
            return response.data
        })
    }

    let currentlyLoading: {cls?: string; version?: string} | undefined = undefined

    async function updateDocumentation(pluginElement?: (LoadOptions & {forceRefresh?: boolean}) | undefined) {
        if (!pluginElement?.cls || !allTypes.value.includes(pluginElement.cls)) {
            editorPlugin.value = undefined
            currentlyLoading = undefined
            return
        }

        const {cls,  version, hash, forceRefresh = false} = pluginElement

        if (currentlyLoading?.cls === cls &&
            currentlyLoading?.version === version &&
            !forceRefresh) {
            return
        }

        if (!forceRefresh &&
            editorPlugin.value?.cls === cls &&
            editorPlugin.value?.version === version) {
            return
        }

        let payload: LoadOptions = {cls, version, hash}

        if (version !== undefined) {
            if (semver.valid(version) !== null ||
                "latest" === version.toString().toLowerCase() ||
                "oldest" === version.toString().toLowerCase()
            ) {
                payload = {
                    ...payload,
                    version,
                }
            }
        }

        currentlyLoading = {
            cls,
            version,
        }

        const pluginData = await load(payload)

        editorPlugin.value = {
            cls,
            version,
            ...pluginData,
        }

        trackPluginDocumentationView(cls)

        forceIncludeProperties.value = Object.keys(pluginElement).filter(k => k !== "cls" && k !== "version" && k !== "forceRefresh")
    }

    const {icons, iconsLoaded, fetchIcons} = usePluginsIcons()

    function groupIcons() {
        return axios.get(`${apiUrlWithoutTenants()}/plugins/icons/groups`, {})
        .then(response => {
            return response.data
        })
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
        listTriggers,
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
    }
})
