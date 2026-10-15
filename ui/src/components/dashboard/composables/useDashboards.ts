import {onMounted, onBeforeUnmount, computed, ref} from "vue";

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
import {chartLoadQueue} from "./chartLoadQueue";



export const isKPIChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.KPI";

export const isTableChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.Table";

/**
 * Charts backed by a chart.js canvas. These dominate a dashboard's memory - a canvas backing store is sized by the
 * chart's box times the device pixel ratio squared - so they are the ones worth unmounting when scrolled out of view.
 */
export const isCanvasChart = (type: string): boolean => [
    "io.kestra.plugin.core.dashboard.chart.Bar",
    "io.kestra.plugin.core.dashboard.chart.Pie",
    "io.kestra.plugin.core.dashboard.chart.TimeSeries",
].includes(type);

export const getChartTitle = (chart: Chart): string => chart.chartOptions?.displayName ?? chart.id;

export const getPropertyValue = (data: Record<string, any> | undefined, property: "value" | "description"): string => data?.results?.[0]?.[property];

export const isPaginationEnabled = (chart: Chart): boolean => chart.chartOptions?.pagination?.enabled ?? false;

export const processFlowYaml = (yaml: string, namespace: string, flow: string): string => yaml.replace(/--NAMESPACE--/g, namespace).replace(/--FLOW--/g, flow);

export function useChartGenerator(props: {chart: Chart; filters: FilterObject[]; showDefault: boolean;}, includeHooks: boolean = true) {
    const percentageShown = computed(() => props.chart?.chartOptions?.numberType === "PERCENTAGE");

    const route = useRoute();

    const dashboardStore = useDashboardStore();

    const {t} = useI18n({useScope: "global"});
    const EMPTY_TEXT = t("dashboards.empty");

    const data = ref();
    const loading = ref(false);
    let isMounted = true;
    onBeforeUnmount(() => {
        isMounted = false;
    });

    async function generate(id: string, pagination?: { pageNumber: number; pageSize: number }, customFilters?: FilterObject[]) {
        const filters = customFilters ?? props.filters.concat(decodeSearchParams(route.query) ?? []);
        const parameters: Parameters = {...pagination, filters: (filters ?? {})};

        loading.value = true;
        try {
            const result = await chartLoadQueue.enqueue(() => {
                // the component may have been unmounted while waiting for a load slot
                if (!isMounted) return Promise.resolve(undefined);

                if (!props.showDefault) {
                    return dashboardStore.generate(id, props.chart.id, parameters);
                }

                if (!props.chart.content){
                    throw new Error("Chart content must exist for preview.");
                }

                const request: Request = {chart: props.chart.content, globalFilter: parameters};
                return dashboardStore.chartPreview(request);
            });

            if (!isMounted) return;
            data.value = result;
            return data.value;
        } finally {
            loading.value = false;
        }
    };

    onMounted(async () => {
        if (includeHooks) await generate(getDashboard(route, "id") as string);
    });

    return {percentageShown, EMPTY_TEXT, data, loading, generate};
}

export * from "../types";