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

import {apiUrl} from "override/utils/route";

import Utils from "../utils/utils";

import type {Dashboard, Chart, Request, Parameters} from "../components/dashboard/composables/useDashboards";
import {useAxios} from "../utils/axios";
import {removeRefPrefix, usePluginsStore} from "./plugins";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import _throttle from "lodash/throttle";
import {useCoreStore} from "./core";
import {useI18n} from "vue-i18n";



export const useDashboardStore = defineStore("dashboard", () => {
    const selectedChart = ref<Chart>();
    const dashboard = ref<Dashboard>();
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

    async function list(options: Record<string, any>) {
        const {sort, ...params} = options;
        const response = await axios.get(`${apiUrl()}/dashboards?size=100${sort ? `&sort=${sort}` : ""}`, {params});

        return response.data;
    }

    async function load(id: Dashboard["id"]) {
        const response = await axios.get(`${apiUrl()}/dashboards/${id}`, {validateStatus});
        let dashboardLoaded: Dashboard;

        if (response.status === 200) dashboardLoaded = response.data;
        else dashboardLoaded = {title: "Default", id, charts: [], sourceCode: ""};

        dashboard.value = dashboardLoaded;
        sourceCode.value = dashboardLoaded.sourceCode ?? ""

        return dashboardLoaded;
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

        const dbId = dashboard.value?.id;
        if (errorsResult.constraints) {
            errors.value = [errorsResult.constraints];
        } else {
            errors.value = undefined;
        }

        if (dbId !== undefined && YAML_UTILS.parse(sourceCode.value).id !== dbId) {
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
        dashboard,
        chartErrors,
        isCreating,
        selectedChart,
        list,
        load,
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
