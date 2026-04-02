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

import type {Dashboard, Chart, Request, Parameters} from "../components/dashboard/types.ts";
import {useAxios} from "../utils/axios";
import {removeRefPrefix, usePluginsStore} from "./plugins";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import _throttle from "lodash/throttle";
import {useCoreStore} from "./core";
import {useI18n} from "vue-i18n";
import {RouteLocation} from "vue-router";

export const useDashboardStore = defineStore("dashboard", () => {
    const dashboardList = ref<{ id: string; title: string; isDefault: boolean }[]>()
    const selectedChart = ref<Chart>();
    const activeDashboard = ref<Dashboard>();
    const defaultDashboards = ref<{
         defaultHomeDashboard?: string,
         defaultFlowOverviewDashboard?: string,
         defaultNamespaceOverviewDashboard?: string,
    }>();
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
        await loadDefaults();
        let isThereADefault = false
        dashboardList.value = res.results.map(dashboard => {
            const isADefaultForThisRoute = isAdminDefinedDefaultDashboard(dashboard.id, route);
            if(isADefaultForThisRoute){
                isThereADefault = true;
            }
            return {...dashboard, isDefault: isADefaultForThisRoute}
        });
        if(!isThereADefault){
            const defaultDashboardBundledInUI = {id: "default", title: t("dashboards.default"), isDefault: true};
            dashboardList.value = [defaultDashboardBundledInUI, ...dashboardList.value];
        }
        return dashboardList.value;
    }

    async function loadDefaults() {
        const response = await axios.get(`${apiUrl()}/dashboards/settings/default-dashboards`);
        defaultDashboards.value = response.data
        return defaultDashboards.value;
    }

    async function saveDefaults(defaultDashboardsRequest: {
        defaultHomeDashboard?: string,
        defaultFlowOverviewDashboard?: string,
        defaultNamespaceOverviewDashboard?: string,
    }) {
        const loadedDef = await loadDefaults();
        const def = {...loadedDef, ...defaultDashboardsRequest}

        const res = await axios.post(`${apiUrlWithoutTenants()}/tenants/main/settings/default-dashboards`, def, {headers: {"Content-Type": "application/json"}});
        defaultDashboards.value = res.data
    }

    const DASHBOARD_ROUTES = ["home", "flows/update", "namespaces/update"]
    type DASHBOARD_TYPE = "DASHBOARD_MAIN" | "DASHBOARD_FLOW" | "DASHBOARD_NAMESPACE";

    const KEY_MAP: Record<string, DASHBOARD_TYPE> = {
        home: "DASHBOARD_MAIN",
        "flows/update": "DASHBOARD_FLOW",
        "namespaces/update": "DASHBOARD_NAMESPACE"
    };

    function getDashboardType(route: RouteLocation) {
        return KEY_MAP[route.name as string];
    }

    const getDashboardId = async (route: RouteLocation): Promise<string> => {
        const routeName = route.name?.toString();
        if(!routeName || !DASHBOARD_ROUTES.includes(routeName)){
            throw new Error("invalid route in getDashboard: "+routeName?.toString())
        }

        // URL
        if(route.params?.dashboard && typeof route.params.dashboard === "string" && route.params.dashboard !== "default"){
            return route.params.dashboard;
        }

        // Localstorage
        const key = getUserDashboardStorageKey(route);
        const userDashboard = localStorage.getItem(key);
        if(userDashboard){
            return userDashboard;
        }

        // tenant default
        const defaultTenantDashboard = await getTenantDefaultDashboardId(route);
        if(defaultTenantDashboard) {
            return defaultTenantDashboard;
        }

        // default
        return "default"
    }

    function getUserDashboardStorageKey(route: RouteLocation){
        const tenant = route.params["tenant"];
        const routeName = route.name?.toString();
        if (!tenant) {
            throw new Error("tenant is mandatory in getUserDashboardStorageKey")
        }
        return `userDashboard/${tenant}/${routeName}`
    }

    async function getTenantDefaultDashboardId(route: RouteLocation) {
        const dashboardType = getDashboardType(route);

        if (!dashboardType) return Promise.resolve(undefined);
        await loadDefaults()
        switch (dashboardType) {
            case "DASHBOARD_MAIN":
                return Promise.resolve(defaultDashboards.value?.defaultHomeDashboard);
            case "DASHBOARD_NAMESPACE":
                return Promise.resolve(defaultDashboards.value?.defaultNamespaceOverviewDashboard);
            case "DASHBOARD_FLOW":
                return Promise.resolve(defaultDashboards.value?.defaultFlowOverviewDashboard);
        }
    }

    const isAdminDefinedDefaultDashboard = (dashboardId: string, route: RouteLocation): boolean => {
        const dashboardType = getDashboardType(route);
        if(dashboardType){
            switch (dashboardType){
                case "DASHBOARD_MAIN": return defaultDashboards.value?.defaultHomeDashboard === dashboardId;
                case "DASHBOARD_NAMESPACE": return defaultDashboards.value?.defaultNamespaceOverviewDashboard === dashboardId;
                case "DASHBOARD_FLOW": return defaultDashboards.value?.defaultFlowOverviewDashboard === dashboardId;
            }
        }
        return false;
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
        parsedSource,
    };
});
