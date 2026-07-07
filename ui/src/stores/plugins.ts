import {defineStore} from "pinia"
import {ref, computed, toRaw, nextTick} from "vue"
import {trackPluginDocumentationView} from "../utils/tabTracking"
import {apiUrlWithoutTenants} from "override/utils/route"
import semver from "semver"
import {API_URL, useApiStore} from "./api"
import InitialFlowSchema from "./flow-schema.json" with {type: "json"}
import {isEntryAPluginElementPredicate, type Plugin, type PluginElement, type PluginIconMap} from "../utils/pluginUtils"
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

export interface PluginIconData {
    flowable: boolean;
    monochrome: boolean;
    hasIcon: boolean;
    /**
     * Only set for icons resolved from the external api.kestra.io plugin catalog (used to show
     * icons for ecosystem plugins the local instance doesn't have installed, e.g. in Blueprints).
     * Points directly at the external per-class endpoint, which serves raw, browser-cacheable SVG.
     */
    iconUrl?: string;
    /**
     * Content hash of the icon bytes, only set for locally-registered icons. KsTaskIcon appends
     * it as a cache-busting query param so the local `/icon.svg` URL can be cached indefinitely —
     * it only needs to change when this hash does.
     */
    hash?: string;
}

// Wire shape returned by the backend's PluginIcon DTO. `icon` (base64, kept for the docs
// generator) is never read for its content on the frontend anymore — only whether it's present,
// since every registered task/trigger class gets an `icons` map entry regardless of whether it
// actually ships an icon (other consumers rely on `flowable` being there either way).
interface RawPluginIcon {
    icon: string | null;
    flowable: boolean;
    monochrome?: boolean;
    hash?: string;
}

function toPluginIconData(raw: RawPluginIcon): PluginIconData {
    return {
        flowable: raw.flowable,
        monochrome: raw.monochrome ?? false,
        hasIcon: raw.icon != null,
        hash: raw.hash,
    }
}

function toPluginIconDataMap(raw: Record<string, RawPluginIcon> | undefined): Record<string, PluginIconData> {
    return Object.fromEntries(
        Object.entries(raw ?? {}).map(([cls, icon]) => [cls, toPluginIconData(icon)]),
    )
}

function usePluginsIcons() {
    const apiStore = useApiStore()

    const iconsLoaded = ref(false)

    const apiIcons = ref<Record<string, PluginIconData>>({})
    const pluginsIcons = ref<Record<string, PluginIconData>>({})
    const iconsPromiseLocal = ref<Promise<Record<string, PluginIconData>>>()
    const iconRequests = new Map<string, Promise<PluginIconData | undefined>>()
    const axios = useClient()

    const icons = computed(() => {
        return {
            ...pluginsIcons.value,
            ...apiIcons.value,
        }
    })

    // Loads the LOCAL instance's full icon catalog in one request — this instance's plugin count
    // is bounded, so a single JSON payload beats one HTTP round-trip per icon for catalog-browsing
    // views (Blueprints, plugin groups, ...). The external api.kestra.io ecosystem catalog is
    // *not* preloaded here (it used to be, at ~8MB) — see `loadEcosystemIcon` below, which resolves
    // those lazily, one class at a time, only for classes actually rendered.
    function fetchIcons() {
        if (iconsLoaded.value) {
            return Promise.resolve(icons.value)
        }

        if (iconsPromiseLocal.value) {
            return iconsPromiseLocal.value
        }

        iconsPromiseLocal.value =
            axios.get<Record<string, RawPluginIcon>>(`${apiUrlWithoutTenants()}/plugins/icons`, {}).then(async response => {
                pluginsIcons.value = toPluginIconDataMap(response.data)
                iconsLoaded.value = true
                return icons.value
            })

        return iconsPromiseLocal.value
    }

    // Resolves an icon for a class the local instance doesn't have registered, from the external
    // api.kestra.io ecosystem catalog (e.g. a Blueprint referencing a plugin that isn't installed).
    // That catalog predates the `monochrome`/`hash` fields added for this redesign, and its
    // per-class endpoint returns raw SVG text rather than JSON, so `monochrome` is derived from a
    // one-time sniff of the bytes, and the icon is rendered by pointing straight at the
    // (browser-cacheable) external URL rather than embedding it.
    function loadEcosystemIcon(cls: string): Promise<PluginIconData | undefined> {
        return apiStore.pluginIcon(cls)
            .then(response => {
                const icon: PluginIconData = {
                    flowable: false,
                    monochrome: response.data.includes("currentColor"),
                    hasIcon: true,
                    iconUrl: `${API_URL}/v1/plugins/icons/${encodeURIComponent(cls)}`,
                }
                apiIcons.value = {...apiIcons.value, [cls]: icon}
                return icon
            })
            .catch(() => undefined)
    }

    // Lazily resolves a single icon instead of preloading the whole local catalog. Meant for views
    // that only ever render a handful of task icons (execution timelines, trigger lists, ...) as
    // well as the ecosystem fallback for catalog-browsing views (see `loadEcosystemIcon` above).
    function loadIcon(cls: string): Promise<PluginIconData | undefined> {
        const cached = icons.value[cls]
        if (cached) {
            return Promise.resolve(cached)
        }

        const pending = iconRequests.get(cls)
        if (pending) {
            return pending
        }

        // Skip the local per-class lookup once the full local catalog is already loaded and
        // simply doesn't have this class — go straight to the ecosystem fallback instead.
        //
        // Otherwise: always answers 200 with `{icon: null}` when the class has no icon (a normal
        // outcome, not every plugin ships one) rather than 404 — a 404 here would trip the shared
        // HTTP client's global error handling, which takes over the whole page for any 404
        // response.
        const localLookup = iconsLoaded.value
            ? Promise.resolve(undefined)
            : axios.get<{icon: RawPluginIcon | null}>(`${apiUrlWithoutTenants()}/plugins/icons/${encodeURIComponent(cls)}`)
                .then(response => {
                    const raw = response.data.icon
                    if (!raw) {
                        return undefined
                    }
                    const icon = toPluginIconData(raw)
                    pluginsIcons.value = {...pluginsIcons.value, [cls]: icon}
                    return icon
                })
                .catch(() => undefined)

        const request = localLookup
            .then(icon => icon ?? loadEcosystemIcon(cls))
            .finally(() => iconRequests.delete(cls))

        iconRequests.set(cls, request)
        return request
    }

    return {
        icons,
        iconsLoaded,
        fetchIcons,
        loadIcon,
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
        await ensurePlugins()

        return (plugins.value ?? []).map(p => ({
            ...p,
            ...Object.fromEntries(excludedElements.map(e => [e, undefined])),
        })).filter(p => Object.entries(p)
                .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                .some(([, value]) => (value as PluginElement[]).length !== 0))
    }

    async function list() {
        const response = await axios.get<{results: Plugin[]; total: number}>(
            `${apiUrlWithoutTenants()}/plugins`,
        )
        plugins.value = response.data.results
        return response.data.results
    }

    // Flat list of every task/trigger class installed on the instance, fetched
    // from the plugins endpoint and cached independently of `plugins` (which
    // other views overwrite with subgroup-shaped, partial payloads).
    const installedPluginTypes = ref<string[]>()
    let installedPluginTypesPending: Promise<string[]> | null = null
    async function loadInstalledPluginTypes(): Promise<string[]> {
        if (installedPluginTypes.value) return installedPluginTypes.value
        if (installedPluginTypesPending) return installedPluginTypesPending
        installedPluginTypesPending = axios
            .get<{results: Plugin[]; total: number}>(`${apiUrlWithoutTenants()}/plugins`)
            .then(response => {
                installedPluginTypes.value = response.data.results.flatMap(p =>
                    Object.entries(p)
                        .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                        .flatMap(([, value]) => (value as PluginElement[]).map(({cls}) => cls)),
                )
                return installedPluginTypes.value
            })
            .finally(() => {
                installedPluginTypesPending = null
            })
        return installedPluginTypesPending
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

    let pluginsPending: Promise<Plugin[]> | null = null
    async function ensurePlugins(): Promise<Plugin[]> {
        if (plugins.value) return plugins.value
        if (pluginsPending) return pluginsPending
        pluginsPending = listWithSubgroup({includeDeprecated: false}).finally(() => {
            pluginsPending = null
        })
        return pluginsPending
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

    const {icons, iconsLoaded, fetchIcons, loadIcon} = usePluginsIcons()

    const groupIcons = ref<PluginIconMap>({})
    let groupIconsPending: Promise<PluginIconMap> | null = null
    function ensureGroupIcons(): Promise<PluginIconMap> {
        if (Object.keys(groupIcons.value).length > 0) return Promise.resolve(groupIcons.value)
        if (groupIconsPending) return groupIconsPending
        groupIconsPending = axios.get<Record<string, RawPluginIcon>>(`${apiUrlWithoutTenants()}/plugins/icons/groups`, {})
            .then(response => {
                groupIcons.value = toPluginIconDataMap(response.data)
                return groupIcons.value
            })
            .finally(() => {
                groupIconsPending = null
            })
        return groupIconsPending
    }

    function findPluginByCls(cls: string | null | undefined): Plugin | null {
        if (!cls || !plugins.value) return null
        const subgroupMatch = plugins.value.find(p => p.subGroup && cls.startsWith(p.subGroup + "."))
        if (subgroupMatch) return subgroupMatch
        for (const plugin of plugins.value) {
            for (const [key, value] of Object.entries(plugin)) {
                if (isEntryAPluginElementPredicate(key, value) && value.some(el => el?.cls === cls)) {
                    return plugin
                }
            }
        }
        return null
    }

    function findPluginByName(name: string | null | undefined, subGroup?: string | null): Plugin | null {
        if (!name || !plugins.value) return null
        if (subGroup) {
            return plugins.value.find(p => p.name === name && p.subGroup === subGroup) ?? null
        }
        return plugins.value.find(p => p.name === name && !p.subGroup) ?? null
    }

    function sidebarPluginsFor(context: {cls?: string | null; owner?: Plugin | null}): Plugin[] {
        const all = plugins.value ?? []
        let ownerGroup: string | undefined
        if (context.owner) {
            ownerGroup = context.owner.group
        } else if (context.cls) {
            ownerGroup = all.find(p =>
                (p.subGroup && context.cls!.startsWith(p.subGroup + "."))
                || context.cls!.startsWith(p.group + "."),
            )?.group
        }
        if (!ownerGroup) return all.filter(p => !p.subGroup)
        const sameGroup = all.filter(p => p.group === ownerGroup)
        const subgroups = sameGroup.filter(p => p.subGroup)
        return subgroups.length > 1 ? subgroups : sameGroup.filter(p => !p.subGroup)
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
        findPluginByCls,
        findPluginByName,
        sidebarPluginsFor,
        list,
        listTriggers,
        listWithSubgroup,
        ensurePlugins,
        installedPluginTypes,
        loadInstalledPluginTypes,
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
        loadIcon,
        groupIcons,
        ensureGroupIcons,
    }
})
