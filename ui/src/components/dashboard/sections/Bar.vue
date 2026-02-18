<template>
    <div :id="containerID" />
    <Bar
        v-if="generated !== undefined"
        :data="parsedData"
        :options="options"
        :plugins="chartOptions?.legend?.enabled ? [customBarLegend] : []"
        :class="props.short ? 'short-chart' : 'chart'"
    />
    <NoData v-else />
</template>

<script setup lang="ts">
    import {PropType, computed, watch} from "vue";
    import moment from "moment";
    import {Bar} from "vue-chartjs";
    import type {TooltipItem, ChartEvent, ActiveElement, ChartData} from "chart.js";

    import NoData from "../../layout/NoData.vue";
    import {Chart, getDashboard} from "../composables/useDashboards";
    import {useChartGenerator} from "../composables/useDashboards";

    import {useBreakpoints, breakpointsElement} from "@vueuse/core";
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm");

    import {customBarLegend} from "../composables/useLegend";
    import {useTheme} from "../../../utils/utils";
    import {defaultConfig, getConsistentHEXColor, chartClick} from "../composables/charts";


    import {useRoute, useRouter} from "vue-router";
    import {Utils} from "@kestra-io/ui-libs";
    import {FilterObject} from "../../../utils/filters";

    const router = useRouter();

    const route = useRoute();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
        short: {type: Boolean, default: false},
    });

    const {data, chartOptions} = props.chart;

    const containerID = `${props.chart.id}__${Math.random()}`;

    const DEFAULTS = {
        display: true,
        stacked: true,
        ticks: {maxTicksLimit: 8},
        grid: {display: false},
    };

    const aggregator = Object.entries(data?.columns ?? {}).filter(([_, v]) => v.agg);

    const theme = useTheme();

    const options = computed(() => {
        return defaultConfig({
            skipNull: true,
            barThickness: 12,
            borderSkipped: false,
            borderColor: "transparent",
            borderWidth: 2,
            plugins: {
                ...(chartOptions?.legend?.enabled
                    ? {
                        customBarLegend: {
                            containerID,
                            uppercase: true,
                        },
                    }
                    : {}),
                tooltip: {
                    enabled: props.short ? false : true,
                    filter: (value: TooltipItem<"bar">) => value.raw,
                    callbacks: {
                        label: (value: TooltipItem<"bar">) => {
                            if (!(value.dataset as any).tooltipText) return "";
                            return `${(value.dataset as any).tooltipText}`;
                        },
                    },
                },
            },
            scales: {
                x: {
                    title: {
                        display: props.short ? false : true,
                        text: chartOptions?.column ? (data?.columns?.[chartOptions.column]?.displayName ?? chartOptions.column) : "",
                    },
                    position: "bottom",
                    ...DEFAULTS,
                    display: props.short ? false : true,
                },
                y: {
                    title: {
                        display: props.short ? false : true,
                        text: aggregator[0][1].displayName ?? aggregator[0][0],
                    },
                    beginAtZero: true,
                    position: "left",
                    ...DEFAULTS,
                    display: verticalLayout.value ? false : (props.short ? false : true),
                    ticks: {
                        ...DEFAULTS.ticks,
                        callback: (value: string | number) => isDurationAgg() ? Utils.humanDuration(value) : value
                    }
                },
            },
            onClick: (_e: ChartEvent, elements: ActiveElement[]) => {
                chartClick(moment, router, route, {}, parsedData.value, elements, "label");
            },
        }, theme.value);
    });

    function isDurationAgg() {
        return aggregator[0][1].field === "DURATION";
    }

    const parsedData = computed((): ChartData<"bar"> => {
        const column = chartOptions?.column ?? "";
        const columns = data?.columns ?? {};

        // Ignore columns with `agg` and dynamically fetch valid ones
        const validColumns = Object.entries(columns)
            .filter(([_, value]) => !(value as Record<string, any>).agg)
            .filter(c => c[0] !== column)// Exclude columns with `agg`
            .map(([key]) => key);

        const grouped: Record<string, Record<string, number>> = {};

        const rawData = generated.value.results as Record<string, any>[] | undefined;
        rawData?.forEach((item: Record<string, any>) => {
            const key = validColumns.map((col) => item[col]).join(", "); // Use '|' as a delimiter
            const itemColumn = item[column] as string;

            if (!grouped[itemColumn]) {
                grouped[itemColumn] = {};
            }
            if (!grouped[itemColumn][key]) {
                grouped[itemColumn][key] = 0;
            }

            grouped[itemColumn][key] += item[aggregator[0][0]];
        });

        const labels = Object.keys(grouped);
        const xLabels = [...new Set(rawData?.map((item: Record<string, any>) => item[column] as string))];

        const datasets = xLabels.flatMap((xLabel) => {
            return Object.entries(grouped[xLabel as string] ?? {}).map(subSectionsEntry => ({
                label: subSectionsEntry[0],
                data: xLabels.map(label => xLabel === label ? subSectionsEntry[1] : 0),
                backgroundColor: getConsistentHEXColor(theme.value, subSectionsEntry[0]),
                tooltipText: `(${subSectionsEntry[0]}): ${aggregator[0][0]} = ${(isDurationAgg() ? Utils.humanDuration(subSectionsEntry[1]) : subSectionsEntry[1])}`,
            }));
        });

        return {labels, datasets};
    });

    const {data: generated, generate} = useChartGenerator(props);

    function refresh() {
        return generate(getDashboard(route, "id")!);
    }

    defineExpose({
        refresh
    });

    watch(() => route.params.filters, () => {
        refresh();
    }, {deep: true});
</script>

<style scoped lang="scss">
    .chart {
        #{--chart-height}: 200px;

        &:not(.with-legend) {
            #{--chart-height}: 231px;
        }

        min-height: var(--chart-height);
        max-height: var(--chart-height);
    }

    .short-chart {
        &:not(.with-legend) {
            #{--chart-height}: 40px;
        }

        min-height: var(--chart-height);
        max-height: var(--chart-height);
    }
</style>
