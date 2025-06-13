import {onMounted, watch, computed, ref} from "vue";
import type {RouteParams, RouteLocation} from "vue-router";
import {useRoute} from "vue-router";
import {useStore} from "vuex";
import {useI18n} from "vue-i18n";

import {decodeSearchParams} from "../../filter/utils/helpers.ts";

export type Dashboard = {
    id: string;
    charts: Chart[];
    title?: string;
    sourceCode?: string;
    [key: string]: unknown;
};

export type Chart = {
    id: string;
    type: string;
    chartOptions?: {
        displayName?: string;
        description?: string;
        width?: number;
        pagination?: {
            enabled?: boolean;
            [key: string]: unknown;
        };
        [key: string]: unknown;
    };
    data?: {      
        columns?: {
            [key: string]: Record<string, any>;
        };
        [key: string]: unknown;
    };
    content?: string;
    source?: {
        type?: string;
        content?: string;
        [key: string]: unknown;
    };
    [key: string]: unknown;
};

export const ALLOWED_CREATION_ROUTES = ["home", "flows/update", "namespaces/update"];

export const STORAGE_KEYS = (params: RouteParams) => {
    const suffix = params.tenant ? `_${params.tenant}` : "";

    return {
        DASHBOARD_MAIN: `dashboard_main${suffix}`,
        DASHBOARD_FLOW: `dashboard_flow${suffix}`,
        DASHBOARD_NAMESPACE: `dashboard_namespace${suffix}`,
    };
};

export const getDashboardID = (route: RouteLocation): string | undefined => {
     if (!ALLOWED_CREATION_ROUTES.includes(route.name as string)) return;

        const map: Record<string, keyof ReturnType<typeof STORAGE_KEYS>> = {
            home: "DASHBOARD_MAIN",
            "flows/update": "DASHBOARD_FLOW",
            "namespaces/update": "DASHBOARD_NAMESPACE"
        };

        const key = map[route.name as string];
        return key ? localStorage.getItem(STORAGE_KEYS(route.params)[key]) || "default" : undefined;
}

import Bar from "../sections/Bar.vue";
import KPI from "../sections/KPI.vue";
import Markdown from "../sections/Markdown.vue";
import Pie from "../sections/Pie.vue";
import Table from "../sections/Table.vue";
import TimeSeries from "../sections/TimeSeries.vue";

export const TYPES: Record<string, any> = {
    "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
    "io.kestra.plugin.core.dashboard.chart.KPI": KPI,
    "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
    "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
    "io.kestra.plugin.core.dashboard.chart.Table": Table,
    "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
};

export const isKPIChart = (type: string): boolean => type === "io.kestra.plugin.core.dashboard.chart.KPI";

export const getChartTitle = (chart: Chart): string => chart.chartOptions?.displayName ?? chart.id;

export const getPropertyValue = (data: Record<string, any>, property: "value" | "description"): string => data.results?.[0]?.[property];

export const isPaginationEnabled = (chart: Chart): boolean => chart.chartOptions?.pagination?.enabled ?? false;

export function useChartGenerator(props: {chart: Chart; filters: string[]; showDefault: boolean;}) {
    const percentageShown = computed(() => props.chart?.chartOptions?.numberType === "PERCENTAGE");

    const route = useRoute();

    const store = useStore();

    const {t} = useI18n({useScope: "global"});
    const EMPTY_TEXT = t("dashboards.empty");

    const data = ref();
    const generate = async (id: string, pagination?: { pageNumber: number; pageSize: number }) => {
        const filters = props.filters.concat(decodeSearchParams(route.query, undefined, []) ?? []);

        if (!props.showDefault) {
            let params = {id, chartId: props.chart.id, filters};

            if (pagination) params = {...params, ...pagination};

            if (filters) params.filters = filters;

            data.value = await store.dispatch("dashboard/generate", params);
        } else {
            const params = {chart: props.chart.content, globalFilter: {filters}};

            if (pagination) params.globalFilter = {...params.globalFilter, ...pagination};

            data.value = await store.dispatch("dashboard/chartPreview", params);
        }

        return data.value;
    };

    onMounted(() => generate(getDashboardID(route) as string));

    watch(route, (changed) => generate(getDashboardID(changed) as string), {deep: true});

    return {percentageShown, EMPTY_TEXT, data, generate};
}
