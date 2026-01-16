import {onMounted, computed, ref} from "vue";

import {useRoute} from "vue-router";
import type {RouteParams, RouteLocation} from "vue-router";

import {useDashboardStore} from "../../../stores/dashboard";

import {useI18n} from "vue-i18n";

import {decodeSearchParams} from "../../filter/utils/helpers";

export const ALLOWED_CREATION_ROUTES = ["home", "flows/update", "namespaces/update"];

export const STORAGE_KEYS = (params: RouteParams) => {
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

export function getDashboard(route: RouteLocation, type: "key" | "id"): string | undefined {
    if (!ALLOWED_CREATION_ROUTES.includes(route.name as string)) return;

    const key = KEY_MAP[route.name as string];

    if (!key) return;

    const storageKey = STORAGE_KEYS(route.params)[key];

    return type === "key" ? storageKey : localStorage.getItem(storageKey) || "default";
};


import {FilterObject} from "../../../utils/filters";
import {Chart, Parameters, Request} from "../types.ts";



export const isKPIChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.KPI";

export const isTableChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.Table";

export const getChartTitle = (chart: Chart): string => chart.chartOptions?.displayName ?? chart.id;

export const getPropertyValue = (data: Record<string, any>, property: "value" | "description"): string => data.results?.[0]?.[property];

export const isPaginationEnabled = (chart: Chart): boolean => chart.chartOptions?.pagination?.enabled ?? false;

export const processFlowYaml = (yaml: string, namespace: string, flow: string): string => yaml.replace(/--NAMESPACE--/g, namespace).replace(/--FLOW--/g, flow);

export function useChartGenerator(props: {chart: Chart; filters: FilterObject[]; showDefault: boolean;}, includeHooks: boolean = true) {
    const percentageShown = computed(() => props.chart?.chartOptions?.numberType === "PERCENTAGE");

    const route = useRoute();

    const dashboardStore = useDashboardStore();

    const {t} = useI18n({useScope: "global"});
    const EMPTY_TEXT = t("dashboards.empty");

    const data = ref();
    async function generate(id: string, pagination?: { pageNumber: number; pageSize: number }, customFilters?: FilterObject[]) {
        const filters = customFilters ?? props.filters.concat(decodeSearchParams(route.query) ?? []);
        const parameters: Parameters = {...pagination, filters: (filters ?? {})};

        if (!props.showDefault) {
            data.value = await dashboardStore.generate(id, props.chart.id, parameters);
        } else {
            if (!props.chart.content){
                throw new Error("Chart content must exist for preview.");
            }

            const request: Request = {chart: props.chart.content, globalFilter: parameters};
            data.value = await dashboardStore.chartPreview(request);
        }

        return data.value;
    };

    onMounted(async () => {
        if (includeHooks) await generate(getDashboard(route, "id") as string);
    });

    return {percentageShown, EMPTY_TEXT, data, generate};
}

export * from "../types";