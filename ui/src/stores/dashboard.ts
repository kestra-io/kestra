import {computed, nextTick, ref, watch} from "vue"
import {defineStore} from "pinia"

const blobResponse = {responseType: "blob" as const}
const downloadHandler = (res: {data: Blob}, filename: string) => {
    const blob = new Blob([res.data], {type: "application/octet-stream"})
    const url = window.URL.createObjectURL(blob)

    Utils.downloadUrl(url, `${filename}.csv`)
}

import {apiUrl} from "override/utils/route"

import * as Utils from "../utils/utils"

import type {Dashboard, Chart} from "../components/dashboard/types.ts"
import {ChartFiltersOverrides, useClient, type DashboardSettings} from "@kestra-io/kestra-sdk"
import * as DashboardsAPI from "@kestra-io/kestra-sdk/dashboards"
import * as TenantsAPI from "@kestra-io/kestra-sdk/tenants"
import {removeRefPrefix, usePluginsStore} from "./plugins"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import _throttle from "lodash/throttle"
import {useCoreStore} from "./core"
import {useUnsavedChangesStore} from "./unsavedChanges"
import {useI18n} from "vue-i18n"
import {RouteLocation} from "vue-router"

export const DEFAULT_DASHBOARD = {
    id: "default",
    title: "",
    deleted: false,
    charts: [],
} as const satisfies Dashboard

export const useDashboardStore = defineStore("dashboard", () => {
    const dashboardList = ref<{ id: string; title: string; isDefault: boolean }[]>()
    const selectedChart = ref<Chart>()
    const activeDashboard = ref<Dashboard>()
    const defaultDashboards = ref<DashboardSettings>()
    const chartErrors = ref<string[]>([])
    const isCreating = ref<boolean>(false)
    const readonlyToastShown = ref(false)

    const sourceCode = ref("")
    const sourceCodeOrigin = ref("")
    const parsedSource = computed<{ id?: string, [key:string]: any } | undefined>((previous) => {
        try {
            return YAML_UTILS.parse(sourceCode.value)
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

    async function list(options: Record<string, any>, route: RouteLocation): Promise<{ id: string; title: string; isDefault: boolean }[]> {
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
            const defaultDashboardBundledInUI = {...DEFAULT_DASHBOARD, title: t("dashboards.default"), isDefault: true}
            dashboardList.value = [defaultDashboardBundledInUI, ...dashboardList.value]
        }
        return dashboardList.value
    }

    async function loadDefaults() {
        // "get default dashboards" lives under a different SDK tag per edition (dashboards in OSS,
        // dashboards-admin in EE) but the same REST path, so go through the raw client to stay
        // edition-agnostic (same approach as the custom-blueprint reads).
        const {data} = await axios.get<DashboardSettings>(`${apiUrl()}/dashboards/settings/default-dashboards`)
        defaultDashboards.value = data
        return defaultDashboards.value
    }

    async function saveDefaults(defaultDashboardsRequest: DashboardSettings) {
        const loadedDef = await loadDefaults()
        const def = {...loadedDef, ...defaultDashboardsRequest}

        // TenantController is hardcoded to the "main" tenant (this OSS build is single-tenant),
        // and its `id` path param isn't the SDK's auto-filled `tenant` param, so it must be passed
        // explicitly here to match.
        defaultDashboards.value = await TenantsAPI.setTenantDefaultDashboard({id: "main", ...def} as Parameters<typeof TenantsAPI.setTenantDefaultDashboard>[0])
    }

    const DASHBOARD_ROUTES = ["home", "flows/update", "namespaces/update"]
    type DASHBOARD_TYPE = "DASHBOARD_MAIN" | "DASHBOARD_FLOW" | "DASHBOARD_NAMESPACE";

    const KEY_MAP: Record<string, DASHBOARD_TYPE> = {
        home: "DASHBOARD_MAIN",
        "flows/update": "DASHBOARD_FLOW",
        "namespaces/update": "DASHBOARD_NAMESPACE",
    }

    function getDashboardType(route: RouteLocation) {
        return KEY_MAP[route.name as string]
    }

    const getDashboardId = async (route: RouteLocation): Promise<string> => {
        const routeName = route.name?.toString()
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
        const routeName = route.name?.toString()
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

    async function load(id: Dashboard["id"]) : Promise<Dashboard | undefined> {
        let data
        try{
            data = await DashboardsAPI.dashboard({id}) as Dashboard
        } catch {
            return undefined
        }

        activeDashboard.value = data
        sourceCode.value = data.sourceCode ?? ""
        sourceCodeOrigin.value = sourceCode.value
        readonlyToastShown.value = false

        return activeDashboard.value
    }

    async function create(source: Dashboard["sourceCode"]) {
        const data = await DashboardsAPI.createDashboard({body: source ?? ""})
        sourceCodeOrigin.value = source ?? ""
        return data
    }

    async function update({id, source}: {id: Dashboard["id"]; source: Dashboard["sourceCode"];}) {
        const data = await DashboardsAPI.updateDashboard({id, body: source ?? ""})
        sourceCodeOrigin.value = source ?? ""
        return data
    }

    async function deleteDashboard(id: Dashboard["id"]) {
        return DashboardsAPI.deleteDashboard({id})
    }

    async function validateDashboard(source: Dashboard["sourceCode"]) {
        return DashboardsAPI.validateDashboard({body: source ?? ""})
    }

    async function generate(id: Dashboard["id"], chartId: Chart["id"], parameters: ChartFiltersOverrides) {
        try {
            return await DashboardsAPI.dashboardChartData({id, chartId, ...parameters} as globalThis.Parameters<typeof DashboardsAPI.dashboardChartData>[0])
        } catch (e: any) {
            if (e.status === 404) return undefined
            throw e
        }
    }

    async function validateChart(source: string) {
        const data = await DashboardsAPI.validateChart({body: source})
        chartErrors.value = data.constraints ? [data.constraints] : []
        return data
    }

    async function chartPreview(request: Parameters<typeof DashboardsAPI.previewChart>[0]) {
        return DashboardsAPI.previewChart(request)
    }

    async function exportDashboard(dashboard: Dashboard, chart: Chart, parameters: ChartFiltersOverrides) {
        const isDefault = dashboard.id === "default"

        const path = isDefault ? "/charts/export/to-csv" : `/${dashboard.id}/charts/${chart.id}/export/to-csv`
        const payload = isDefault ? {chart: chart.content, globalFilter: parameters} : parameters

        const filename = `chart__${chart.id}`

        return axios
            .post(`${apiUrl()}/dashboards${path}`, payload, blobResponse)
            .then((res) => downloadHandler(res, filename))
    }

    const pluginsStore = usePluginsStore()

    const InitialSchema = {}

    const schema = computed<{
            definitions: any,
            $ref: string,
    }>(() =>  {
        return pluginsStore.schemaType?.dashboard ?? InitialSchema
    })

    const definitions = computed<Record<string, any>>(() =>  {
        return schema.value.definitions ?? {}
    })

    function recursivelyLoopUpSchemaRef(a: any, defs: Record<string, any>): any {
        if (a.$ref) {
            const refKey = removeRefPrefix(a.$ref)
            return recursivelyLoopUpSchemaRef(defs[refKey], defs)
        }
        return a
    }

    const rootSchema = computed<Record<string, any> | undefined>(() => {
        return recursivelyLoopUpSchemaRef(schema.value, definitions.value)
    })

    const rootProperties = computed<Record<string, any> | undefined>(() => {
        return rootSchema.value?.properties
    })

    async function loadChart(chart: any) {
        const yamlChart = YAML_UTILS.stringify(chart)
        if(selectedChart.value?.content === yamlChart){
            return {
                error: chartErrors.value.length > 0 ? chartErrors.value[0] : null,
                data: selectedChart.value ? {...selectedChart.value, raw: chart} : null,
                raw: chart,
            }
        }
        const result: { error: string | null; data: null | {
            id?: string;
            name?: string;
            type?: string;
            chartOptions?: Record<string, any>;
            dataFilters?: any[];
            charts?: any[];
        }; raw: any } = {
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

        selectedChart.value = typeof result.data === "object"
            ? {
                ...result.data,
                chartOptions: {
                    ...result.data?.chartOptions,
                    width: 12,
                },
            } as any
            : undefined
        chartErrors.value = [result.error].filter(e => e !== null)

        return result
    }

    const errors = ref<string[] | undefined>()
    const warnings = ref<string[] | undefined>()
    const coreStore = useCoreStore()

    const {t} = useI18n()

    watch(sourceCode, _throttle(async () => {
        const errorsResult = await validateDashboard(sourceCode.value)

        const dbId = activeDashboard.value?.id
        if (errorsResult.constraints) {
            errors.value = [errorsResult.constraints]
        } else {
            errors.value = undefined
        }

        if (!isCreating.value && dbId !== undefined && YAML_UTILS.parse(sourceCode.value).id !== dbId) {
            if (!readonlyToastShown.value) {
                readonlyToastShown.value = true
                coreStore.message = {
                    variant: "warning",
                    title: t("readonly property"),
                    message: t("dashboards.edition.id readonly"),
                }
            }

            await nextTick()
            if(sourceCode.value && dbId){
                sourceCode.value = YAML_UTILS.replaceBlockWithPath({
                    source: sourceCode.value,
                    path: "id",
                    newContent: dbId,
                })
            }
        }
    }, 300, {trailing: true, leading: false}))

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
        warnings,

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
