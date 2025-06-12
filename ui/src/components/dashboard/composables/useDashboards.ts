import {onMounted, watch, computed, ref} from "vue";
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
    content?: string;
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
    source?: {
        type?: string;
        content?: string;
        [key: string]: unknown;
    };
    [key: string]: unknown;
};

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

export const isKPIChart = (type: string) => type === "io.kestra.plugin.core.dashboard.chart.KPI";

export const getChartTitle = (chart: Chart): string => chart.chartOptions?.displayName ?? chart.id;

export const getPropertyValue = (data: Record<string, any>, property: "value" | "description"): string => data.results?.[0]?.[property];

export function useChartGenerator(props: {chart: Chart; filters: string[]; showDefault: boolean;}) {
    const percentageShown = computed(() => props.chart?.chartOptions?.numberType === "PERCENTAGE");

    const route = useRoute();

    const store = useStore();

    const {t} = useI18n({useScope: "global"});
    const EMPTY_TEXT = t("dashboards.empty");

    const data = ref();
    const generate = async (id: string) => {
        const filters = props.filters.concat(decodeSearchParams(route.query, undefined, []) ?? []);
        
        if (!props.showDefault) {
            data.value = await store.dispatch("dashboard/generate", {id, chartId: props.chart.id, filters});
        } else {
            data.value = await store.dispatch("dashboard/chartPreview", {chart: props.chart.content, globalFilter: {filters}});
        }

        return data.value;
    };

    onMounted(() => generate(route.params.id as string));

    watch(route, (changed) => generate(changed.params.id as string));

    return {percentageShown, EMPTY_TEXT, data, generate};
}
