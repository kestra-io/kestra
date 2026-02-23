import {computed, nextTick, ref, watch} from "vue";
import {defineStore} from "pinia";

import type {AxiosRequestConfig, AxiosResponse} from "axios";

const header: AxiosRequestConfig = {headers: {"Content-Type": "application/x-yaml"}};
const response: AxiosRequestConfig = {responseType: "blob" as const};
const validateStatus = (status: number) => status === 200 || status === 404;
const downloadHandler = (response: AxiosResponse, filename: string) => {
    const blob = new Blob([response.data], {type: "application/octet-stream"});
    const url = window.URL.createObjectURL(blob);

    Utils.downloadUrl(url, `${filename}.csv`);
};

import {apiUrl, apiUrlWithoutTenants} from "override/utils/route";

import Utils from "../utils/utils";

import type {Dashboard, Chart, Request, Parameters} from "../components/dashboard/composables/useDashboards";
import {useAxios} from "../utils/axios";
import {removeRefPrefix, usePluginsStore} from "./plugins";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import _throttle from "lodash/throttle";
import {useCoreStore} from "./core";
import {useI18n} from "vue-i18n";
import {RouteLocation, RouteParams} from "vue-router";

export const useDashboardStore = defineStore("dashboard", () => {
    const selectedChart = ref<Chart>();
    const activeDashboard = ref<Dashboard>();
    const defaultDashboards = ref<{
         defaultHomeDashboard?: string,
         defaultFlowOverviewDashboard?: string,
         defaultNamespaceOverviewDashboard?: string,
    }>();
    const computedDashboards = ref<{
        defaultHomeDashboard: string,
        defaultFlowOverviewDashboard: string,
        defaultNamespaceOverviewDashboard: string,
    }>({defaultHomeDashboard: "default", defaultNamespaceOverviewDashboard: "default", defaultFlowOverviewDashboard: "default"});
    const chartErrors = ref<string[]>([]);
    const isCreating = ref<boolean>(false);

    const sourceCode = ref("")
    const parsedSource = computed<{ id?: string, [key:string]: any } | undefined>((previous) => {
        try {
            return YAML_UTILS.parse(sourceCode.value);
        } catch {
            return previous;
        }
    })

    const axios = useAxios();

    async function list(options: Record<string, any>, route: RouteLocation): Promise<{ id: string; title: string; isDefault: boolean }[]> {
        const {sort, ...params} = options;
        const response = await axios.get(`${apiUrl()}/dashboards?size=100${sort ? `&sort=${sort}` : ""}`, {params});
        const res = response.data as { results: { id: string; title: string }[]};
        return res.results.map(dashboard => {return {...dashboard, isDefault: isDefaultDashboard(dashboard.id, route)}})
    }

    async function loadDefaults() {
        if(!defaultDashboards.value){
            const response = await axios.get(`${apiUrl()}/dashboards/settings/default-dashboards`);
            defaultDashboards.value = response.data
        }
        if(defaultDashboards.value){
            updateComputedDashboardsFromLoadedDefaults(defaultDashboards.value)
        }
        return defaultDashboards.value;
    }

    function updateComputedDashboardsFromLoadedDefaults(loadedDefaults: {
        defaultHomeDashboard?: string,
        defaultFlowOverviewDashboard?: string,
        defaultNamespaceOverviewDashboard?: string,
    }){
        if(loadedDefaults.defaultHomeDashboard){
            computedDashboards.value.defaultHomeDashboard = loadedDefaults.defaultHomeDashboard;
        }
        if(loadedDefaults.defaultNamespaceOverviewDashboard){
            computedDashboards.value.defaultNamespaceOverviewDashboard = loadedDefaults.defaultNamespaceOverviewDashboard;
        }
        if(loadedDefaults.defaultFlowOverviewDashboard){
            computedDashboards.value.defaultFlowOverviewDashboard = loadedDefaults.defaultFlowOverviewDashboard;
        }
    }

    async function saveDefaults(defaultDashboardsRequest: {
        defaultHomeDashboard?: string,
        defaultFlowOverviewDashboard?: string,
        defaultNamespaceOverviewDashboard?: string,
    }) {
        const loadedDef = await loadDefaults();
        const def = {...loadedDef, ...defaultDashboardsRequest}

        await axios.post(`${apiUrlWithoutTenants()}/tenants/main/settings/default-dashboards`, def, {headers: {"Content-Type": "application/json"}});
        defaultDashboards.value = def
    }

    const STORAGE_KEYS = (params: RouteParams) => {
        const suffix = params.tenant ? `_${params.tenant}` : "";

        return {
            DASHBOARD_MAIN: `dashboard_main${suffix}`,
            DASHBOARD_FLOW: `dashboard_flow${suffix}`,
            DASHBOARD_NAMESPACE: `dashboard_namespace${suffix}`,
        };
    };


    const KEY_MAP: Record<string, keyof ReturnType<typeof STORAGE_KEYS>> = {
        home: "DASHBOARD_MAIN",
        "flows/update": "DASHBOARD_FLOW",
        "namespaces/update": "DASHBOARD_NAMESPACE"
    };

    function getDashboardType(route: RouteLocation) {
        if (!route.params["tenant"]) {
            throw new Error("tenant is mandatory in getDashboardType")
        }
        if (route.params["tenant"] != "main") {
            throw new Error("tenant other than main unhandled yet")// TODO
        }

        /*if (!ALLOWED_CREATION_ROUTES.includes(route.name as string)) return;*/

        const key = KEY_MAP[route.name as string];
        return key;
    }

    const DASHBOARD_ROUTES = ["home", "flows/update", "namespaces/update"]

    const getDashboardId = async (route: RouteLocation): Promise<string> => {
        if(!route.name || !DASHBOARD_ROUTES.includes(route.name.toString())){
            throw new Error("invalid route in getDashboard: "+route.name?.toString())
        }

        // URL
        if(route.params?.dashboard && typeof route.params.dashboard === "string"){
            return route.params.dashboard;
        }

        // Localstorage
        // TODO

        // tenant default
        const defaultTenantDashboard = await getTenantDefaultDashboardId(route);
        if(defaultTenantDashboard) {
            return defaultTenantDashboard;
        }

        // default
        return "default"
    }

    async function getTenantDefaultDashboardId(route: RouteLocation) {
        const dashboardType = getDashboardType(route);

        if (!dashboardType) return Promise.resolve(undefined);
        await loadDefaults()
        switch (dashboardType) {
            case "DASHBOARD_MAIN":
                return Promise.resolve(computedDashboards.value.defaultHomeDashboard);
            case "DASHBOARD_NAMESPACE":
                return Promise.resolve(computedDashboards.value.defaultNamespaceOverviewDashboard);
            case "DASHBOARD_FLOW":
                return Promise.resolve(computedDashboards.value.defaultFlowOverviewDashboard);
        }
    }

    const isDefaultDashboard = (dashboardId: string, route: RouteLocation): boolean => {
        const dashboardType = getDashboardType(route);
        if(!dashboardType){
            return false;
        }
        switch (dashboardType){
            case "DASHBOARD_MAIN": return computedDashboards.value.defaultHomeDashboard === dashboardId;
            case "DASHBOARD_NAMESPACE": return computedDashboards.value.defaultNamespaceOverviewDashboard === dashboardId;
            case "DASHBOARD_FLOW": return computedDashboards.value.defaultFlowOverviewDashboard === dashboardId;
        }
    }

    async function load(id: Dashboard["id"]) : Promise<Dashboard | undefined> {
        let response
        try{
            response = await axios.get(`${apiUrl()}/dashboards/${id}`, {validateStatus});
        } catch {
            return undefined
        }

        if (response.status === 404){
            return undefined;
        }

        activeDashboard.value = response.data;
        sourceCode.value = response.data.sourceCode ?? ""

        return activeDashboard.value;
    }

    async function create(source: Dashboard["sourceCode"]) {
        const response = await axios.post(`${apiUrl()}/dashboards`, source, header);
        return response.data;
    }

    async function update({id, source}: {id: Dashboard["id"]; source: Dashboard["sourceCode"];}) {
        const response = await axios.put(`${apiUrl()}/dashboards/${id}`, source, header);
        return response.data;
    }

    async function deleteDashboard(id: Dashboard["id"]) {
        const response = await axios.delete(`${apiUrl()}/dashboards/${id}`);
        return response.data;
    }

    async function validateDashboard(source: Dashboard["sourceCode"]) {
        const response = await axios.post(`${apiUrl()}/dashboards/validate`, source, header);
        return response.data;
    }

    async function generate(id: Dashboard["id"], chartId: Chart["id"], parameters: Parameters) {
        const response = await axios.post(`${apiUrl()}/dashboards/${id}/charts/${chartId}`, parameters, {validateStatus});
        return response.data;
    }

    async function validateChart(source: string) {
        const response = await axios.post(`${apiUrl()}/dashboards/validate/chart`, source, header);
        chartErrors.value = response.data;
        return response.data;
    }

    async function chartPreview(request: Request) {
        const response = await axios.post(`${apiUrl()}/dashboards/charts/preview`, request);
        return response.data;
    }

    async function exportDashboard(dashboard: Dashboard, chart: Chart, parameters: Parameters) {
        const isDefault = dashboard.id === "default";

        const path = isDefault ? "/charts/export/to-csv" : `/${dashboard.id}/charts/${chart.id}/export/to-csv`;
        const payload = isDefault ? {chart: chart.content, globalFilter: parameters} : parameters;

        const filename = `chart__${chart.id}`;

        return axios
            .post(`${apiUrl()}/dashboards${path}`, payload, response)
            .then((res) => downloadHandler(res, filename));
    }

    const pluginsStore = usePluginsStore();

    const InitialSchema = {}

    const schema = computed<{
            definitions: any,
            $ref: string,
    }>(() =>  {
        return pluginsStore.schemaType?.dashboard ?? InitialSchema;
    })

    const definitions = computed<Record<string, any>>(() =>  {
        return schema.value.definitions ?? {};
    });

    function recursivelyLoopUpSchemaRef(a: any, definitions: Record<string, any>): any {
        if (a.$ref) {
            const ref = removeRefPrefix(a.$ref);
            return recursivelyLoopUpSchemaRef(definitions[ref], definitions);
        }
        return a;
    }

    const rootSchema = computed<Record<string, any> | undefined>(() => {
        return recursivelyLoopUpSchemaRef(schema.value, definitions.value);
    });

    const rootProperties = computed<Record<string, any> | undefined>(() => {
        return rootSchema.value?.properties;
    });

    async function loadChart(chart: any) {
        const yamlChart = YAML_UTILS.stringify(chart);
        if(selectedChart.value?.content === yamlChart){
            return {
                error: chartErrors.value.length > 0 ? chartErrors.value[0] : null,
                data: selectedChart.value ? {...selectedChart.value, raw: chart} : null,
                raw: chart
            };
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
            raw: {}
        };
        const errors = await validateChart(yamlChart);

        if (errors.constraints) {
            result.error = errors.constraints;
        } else {
            result.data = {...chart, content: yamlChart, raw: chart};
        }

        selectedChart.value = typeof result.data === "object"
            ? {
                ...result.data,
                chartOptions: {
                    ...result.data?.chartOptions,
                    width: 12
                }
            } as any
            : undefined;
        chartErrors.value = [result.error].filter(e => e !== null);

        return result;
    }

    const errors = ref<string[] | undefined>();
    const warnings = ref<string[] | undefined>();
    const coreStore = useCoreStore();

    const {t} = useI18n()

    watch(sourceCode, _throttle(async () => {
        const errorsResult = await validateDashboard(sourceCode.value);

        const dbId = activeDashboard.value?.id;
        if (errorsResult.constraints) {
            errors.value = [errorsResult.constraints];
        } else {
            errors.value = undefined;
        }

        if (!isCreating.value && dbId !== undefined && YAML_UTILS.parse(sourceCode.value).id !== dbId) {
            coreStore.message = {
                variant: "error",
                title: t("readonly property"),
                message: t("dashboards.edition.id readonly"),
            };

            await nextTick();
            if(sourceCode.value && dbId){
                sourceCode.value = YAML_UTILS.replaceBlockWithPath({
                    source: sourceCode.value,
                    path: "id",
                    newContent: dbId,
                });
            }
        }
    }, 300, {trailing: true, leading: false}));

    return {
        activeDashboard,
        chartErrors,
        isCreating,
        selectedChart,
        list,
        getDashboardId,
        isDefaultDashboard,
        load,
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
        parsedSource,
    };
});
