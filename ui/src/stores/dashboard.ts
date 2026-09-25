import {computed, ref, watch} from "vue"
import {defineStore} from "pinia"

import type {AxiosLikeConfig, AxiosLikeResponse} from "@kestra-io/kestra-sdk"

const response: AxiosLikeConfig = {responseType: "blob" as const}
const validateStatus = (status: number) => status === 200 || status === 404
/** Returns false when the export carried no rows: the backend answers 200 with an empty body,
 *  and writing that out hands the user a 0 byte file with no clue anything went wrong. */
const downloadHandler = (res: AxiosLikeResponse, filename: string, extension: string): boolean => {
    const blob = new Blob([res.data], {type: "application/octet-stream"})
    if (blob.size === 0) return false

    Utils.downloadUrl(window.URL.createObjectURL(blob), `${filename}.${extension}`)
    return true
}

import {apiUrl, apiUrlWithoutTenants, basePath} from "override/utils/route"
import {useMiscStore} from "override/stores/misc"

import * as Utils from "../utils/utils"
import {routeFamily} from "../utils/routeFamily"

import type {Dashboard, Chart, DashboardSettings} from "../components/dashboard/types.ts"
import {useClient, type ChartFiltersOverrides} from "@kestra-io/kestra-sdk"
import * as DashboardsAPI from "@kestra-io/kestra-sdk/dashboards"
import {removeRefPrefix, usePluginsStore, type JsonSchemaDef, type RootJsonSchema} from "./plugins"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {useUnsavedChangesStore} from "./unsavedChanges"
import {useBookmarksStore} from "./bookmarks"
import type {RouteLocation} from "vue-router"
import type {KestraHttpError} from "../utils/kestraHttp"

type ParsedDashboardSource = {id?: string} & Record<string, unknown>
type DashboardListOptions = Omit<NonNullable<Parameters<typeof DashboardsAPI.searchDashboards>[0]>, "sort"> & {sort?: string}
type EditableChart = Omit<Chart, "chartOptions"> & {chartOptions?: Partial<NonNullable<Chart["chartOptions"]>>}
type LoadedChart = EditableChart & {raw: EditableChart}

interface LoadChartResult {
    error: string | null;
    data: LoadedChart | null;
    raw: Record<string, unknown>;
}

export const DEFAULT_DASHBOARD = {
    id: "default",
    title: "",
    deleted: false,
    charts: [],
} as const satisfies Dashboard

export const useDashboardStore = defineStore("dashboard", () => {
    const dashboardList = ref<{ id: string; title: string; isDefault: boolean }[]>()
    const selectedChart = ref<EditableChart>()
    const activeDashboard = ref<Dashboard>()
    const defaultDashboards = ref<DashboardSettings>()
    const defaultDefinitions = ref<{
        main: string,
        flow: string,
        namespace: string,
    }>()
    const chartErrors = ref<string[]>([])
    const isCreating = ref<boolean>(false)
    // const readonlyToastShown = ref(false)

    const sourceCode = ref("")
    const sourceCodeOrigin = ref("")
    const parsedSource = computed<ParsedDashboardSource | undefined>((previous) => {
        try {
            return YAML_UTILS.parse(sourceCode.value) as ParsedDashboardSource
        } catch {
            return previous
        }
    })

    const haveChange = computed(() => sourceCodeOrigin.value !== sourceCode.value)

    const unsavedChangesStore = useUnsavedChangesStore()

    watch(haveChange, (newValue) => {
        unsavedChangesStore.unsavedChange = newValue
    })

    const axios = useClient()

    async function list(options: DashboardListOptions, route: RouteLocation): Promise<{ id: string; title: string; isDefault: boolean }[]> {
        const {sort, ...params} = options
        const res = await DashboardsAPI.searchDashboards({...params, size: 100, sort: sort ? [sort] : undefined})
        await loadDefaults()
        let isThereADefault = false
        dashboardList.value = (res.results as { id: string; title: string }[]).map(dashboard => {
            const isADefaultForThisRoute = isAdminDefinedDefaultDashboard(dashboard.id, route)
            if(isADefaultForThisRoute){
                isThereADefault = true
            }
            return {...dashboard, isDefault: isADefaultForThisRoute}
        })
        if(!isThereADefault){
            const defaultDashboardBundledInUI = {...DEFAULT_DASHBOARD, title: "default", isDefault: true}
            dashboardList.value = [defaultDashboardBundledInUI, ...dashboardList.value]
        }
        return dashboardList.value
    }

    /** Loads the tenant default dashboards; an instance that cannot store dashboards has none and does not serve the route. */
    async function loadDefaults() {
        if (useMiscStore().configs?.isCustomDashboardsEnabled === false) {
            defaultDashboards.value = {}
            return defaultDashboards.value
        }

        // "get default dashboards" lives under a different SDK tag per edition (dashboards in OSS,
        // dashboards-admin in EE) but the same REST path, so go through the raw client to stay
        // edition-agnostic (same approach as the custom-blueprint reads).
        const {data} = await axios.get<DashboardSettings>(`${apiUrl()}/dashboards/settings/default-dashboards`)
        defaultDashboards.value = data
        return defaultDashboards.value
    }

    async function loadDefaultDefinitions() {
        if (!defaultDefinitions.value) {
            const res = await axios.get(`${apiUrl()}/dashboards/defaults/definitions`)
            defaultDefinitions.value = res.data
        }
        return defaultDefinitions.value!
    }

    // side-effect-free lookups for autocompletion, deliberately not going through
    // list()/load() which mutate dashboardList/activeDashboard and would clobber
    // whatever the user is currently viewing/editing elsewhere in the app.
    async function searchIds(): Promise<{ id: string; title?: string }[]> {
        const res = await axios.get(`${apiUrl()}/dashboards?size=100`)
        return (res.data as { results: { id: string; title?: string }[] }).results
    }

    async function chartsById(id: Dashboard["id"]): Promise<Chart[]> {
        const res = await axios.get(`${apiUrl()}/dashboards/${id}`, {validateStatus})
        if (res.status === 404) return []
        return (res.data as Dashboard).charts ?? []
    }

    async function saveDefaults(defaultDashboardsRequest: DashboardSettings) {
        const loadedDef = await loadDefaults()
        const def = {...loadedDef, ...defaultDashboardsRequest}

        const tenantId = basePath().split("/").filter(Boolean)[2]
        if (!tenantId) {
            throw new Error("Cannot save the default dashboards: no tenant is selected.")
        }

        const {data} = await axios.post<DashboardSettings>(`${apiUrlWithoutTenants()}/tenants/${tenantId}/settings/default-dashboards`, def)
        defaultDashboards.value = data
    }

    const DASHBOARD_ROUTES = ["home", "flows/update", "namespaces/update"]
    type DASHBOARD_TYPE = "DASHBOARD_MAIN" | "DASHBOARD_FLOW" | "DASHBOARD_NAMESPACE";

    const KEY_MAP: Record<string, DASHBOARD_TYPE> = {
        home: "DASHBOARD_MAIN",
        "flows/update": "DASHBOARD_FLOW",
        "namespaces/update": "DASHBOARD_NAMESPACE",
    }

    function getDashboardType(route: RouteLocation) {
        return KEY_MAP[routeFamily(route.name)]
    }

    const getDashboardId = async (route: RouteLocation): Promise<string> => {
        const routeName = route.name ? routeFamily(route.name) : undefined
        if(!routeName || !DASHBOARD_ROUTES.includes(routeName)){
            throw new Error("invalid route in getDashboard: "+routeName?.toString())
        }

        // URL
        if(route.params?.dashboard && typeof route.params.dashboard === "string" && route.params.dashboard !== "default"){
            return route.params.dashboard
        }

        // Localstorage
        const key = getUserDashboardStorageKey(route)
        const userDashboard = localStorage.getItem(key)
        if(userDashboard){
            return userDashboard
        }

        // tenant default
        const defaultTenantDashboard = await getTenantDefaultDashboardId(route)
        if(defaultTenantDashboard) {
            return defaultTenantDashboard
        }

        // default
        return "default"
    }

    function getUserDashboardStorageKey(route: RouteLocation){
        const tenant = route.params["tenant"]
        const routeName = route.name ? routeFamily(route.name) : undefined
        if (!tenant) {
            throw new Error("tenant is mandatory in getUserDashboardStorageKey")
        }
        return `userDashboard/${tenant}/${routeName}`
    }

    async function getTenantDefaultDashboardId(route: RouteLocation) {
        const dashboardType = getDashboardType(route)

        if (!dashboardType) return Promise.resolve(undefined)
        await loadDefaults()
        switch (dashboardType) {
            case "DASHBOARD_MAIN":
                return Promise.resolve(defaultDashboards.value?.defaultHomeDashboard)
            case "DASHBOARD_NAMESPACE":
                return Promise.resolve(defaultDashboards.value?.defaultNamespaceOverviewDashboard)
            case "DASHBOARD_FLOW":
                return Promise.resolve(defaultDashboards.value?.defaultFlowOverviewDashboard)
        }
    }

    const isAdminDefinedDefaultDashboard = (dashboardId: string, route: RouteLocation): boolean => {
        const dashboardType = getDashboardType(route)
        if(dashboardType){
            switch (dashboardType){
                case "DASHBOARD_MAIN": return defaultDashboards.value?.defaultHomeDashboard === dashboardId
                case "DASHBOARD_NAMESPACE": return defaultDashboards.value?.defaultNamespaceOverviewDashboard === dashboardId
                case "DASHBOARD_FLOW": return defaultDashboards.value?.defaultFlowOverviewDashboard === dashboardId
            }
        }
        return false
    }

    const silent = {showMessageOnError: false} as Parameters<typeof DashboardsAPI.dashboard>[1]

    async function load(id: Dashboard["id"]) : Promise<Dashboard | undefined> {
        let data
        try{
            data = await DashboardsAPI.dashboard({id}, silent) as Dashboard
        } catch {
            return undefined
        }

        activeDashboard.value = data
        sourceCode.value = data.sourceCode ?? ""
        sourceCodeOrigin.value = sourceCode.value

        return activeDashboard.value
    }

    /** Dashboard writes are Enterprise-only routes, absent from the OSS SDK, so they go through the raw client; the backend only routes application/x-yaml, not application/yaml. */
    const yaml = {headers: {"Content-Type": "application/x-yaml"}}

    async function create(source: Dashboard["sourceCode"]) {
        const {data} = await axios.post(`${apiUrl()}/dashboards`, source ?? "", yaml)
        sourceCodeOrigin.value = source ?? ""
        return data
    }

    async function update({id, source}: {id: Dashboard["id"]; source: Dashboard["sourceCode"];}) {
        const {data} = await axios.put(`${apiUrl()}/dashboards/${id}`, source ?? "", yaml)
        sourceCodeOrigin.value = source ?? ""
        return data
    }

    async function deleteDashboard(id: Dashboard["id"]) {
        const {data: deleted} = await axios.delete(`${apiUrl()}/dashboards/${id}`)

        const bookmarksStore = useBookmarksStore()
        const encodedId = encodeURIComponent(id)
        bookmarksStore.updateAll(bookmarksStore.pages.filter(({path}) => {
            const segments = path.split(/[?#]/)[0].split("/")
            const index = segments.indexOf("dashboards")
            return index === -1 || segments[index + 1] !== encodedId
        }))

        return deleted
    }

    async function validateDashboard(source: Dashboard["sourceCode"]) {
        const {data} = await axios.post(`${apiUrl()}/dashboards/validate`, source ?? "", yaml)
        return data
    }

    async function generate(id: Dashboard["id"], chartId: Chart["id"], parameters: ChartFiltersOverrides) {
        try {
            const {data} = await axios.post(`${apiUrl()}/dashboards/${id}/charts/${chartId}`, parameters, {showMessageOnError: false} as AxiosLikeConfig)
            return data
        } catch (e: unknown) {
            if ((e as KestraHttpError).status === 404) return undefined
            throw e
        }
    }

    async function validateChart(source: string) {
        const {data} = await axios.post(`${apiUrl()}/dashboards/validate/chart`, source, yaml)
        chartErrors.value = data.constraints ? [data.constraints] : []
        return data
    }

    async function chartPreview(request: Parameters<typeof DashboardsAPI.previewChart>[0]) {
        return DashboardsAPI.previewChart(request)
    }

    /** Resolves to false when the chart had nothing to export, so the caller can tell the user
     *  instead of silently downloading an empty file. */
    async function exportDashboard(dashboard: Dashboard, chart: Chart, parameters: ChartFiltersOverrides, format: "CSV" | "ION" = "CSV"): Promise<boolean> {
        const isDefault = dashboard.id === "default"

        const path = isDefault ? "/charts/export" : `/${dashboard.id}/charts/${chart.id}/export`
        const payload = isDefault ? {chart: chart.content, globalFilter: parameters} : parameters

        const filename = `chart__${chart.id}`

        return axios
            .post(`${apiUrl()}/dashboards${path}?format=${format}`, payload, response)
            .then((res) => downloadHandler(res, filename, format.toLowerCase()))
    }

    const pluginsStore = usePluginsStore()

    const InitialSchema = {definitions: {}, $ref: ""}

    const schema = computed<RootJsonSchema>(() =>  {
        return pluginsStore.schemaType?.dashboard ?? InitialSchema
    })

    const definitions = computed<Record<string, JsonSchemaDef>>(() =>  {
        return schema.value.definitions ?? {}
    })

    function recursivelyLoopUpSchemaRef(schemaValue: JsonSchemaDef | undefined, defs: Record<string, JsonSchemaDef>): JsonSchemaDef | undefined {
        if (schemaValue?.$ref) {
            const refKey = removeRefPrefix(schemaValue.$ref)
            return recursivelyLoopUpSchemaRef(defs[refKey], defs)
        }
        return schemaValue
    }

    const rootSchema = computed<JsonSchemaDef | undefined>(() => {
        return recursivelyLoopUpSchemaRef(schema.value, definitions.value)
    })

    const rootProperties = computed<Record<string, JsonSchemaDef> | undefined>(() => {
        return rootSchema.value?.properties
    })

    async function loadChart(chart: EditableChart): Promise<LoadChartResult> {
        const yamlChart = YAML_UTILS.stringify(chart)
        if(selectedChart.value?.content === yamlChart){
            return {
                error: chartErrors.value.length > 0 ? chartErrors.value[0] : null,
                data: selectedChart.value ? {...selectedChart.value, raw: chart} : null,
                raw: chart,
            }
        }
        const result: LoadChartResult = {
            error: null,
            data: null,
            raw: {},
        }
        const errors = await validateChart(yamlChart)

        if (errors.constraints) {
            result.error = errors.constraints
        } else {
            result.data = {...chart, content: yamlChart, raw: chart}
        }

        selectedChart.value = result.data
            ? {
                ...result.data,
                chartOptions: {
                    ...result.data?.chartOptions,
                    width: 12,
                },
            }
            : undefined
        chartErrors.value = [result.error].filter(e => e !== null)

        return result
    }

    const errors = ref<string[] | undefined>()

    return {
        activeDashboard,
        chartErrors,
        isCreating,
        selectedChart,
        list,
        getDashboardId,
        load,
        getUserDashboardStorageKey,
        defaultDashboards,
        loadDefaults,
        defaultDefinitions,
        loadDefaultDefinitions,
        searchIds,
        chartsById,
        saveDefaults,
        create,
        update,
        delete: deleteDashboard,
        validateDashboard,
        generate,
        validateChart,
        chartPreview,
        export: exportDashboard,
        loadChart,
        errors,

        schema,
        definitions,
        rootSchema,
        rootProperties,
        sourceCode,
        sourceCodeOrigin,
        haveChange,
        parsedSource,
    }
})
