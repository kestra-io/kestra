<template>
    <div
        class="d-flex flex-row align-items-center justify-content-center h-100"
    >
        <div>
            <component
                :is="chartOptions?.graphStyle === 'PIE' ? Pie : Doughnut"
                v-if="generated !== undefined"
                :data="parsedData"
                :options="options"
                :plugins="
                    chartOptions?.legend?.enabled
                        ? [isDuration ? totalsDurationLegend : totalsLegend, centerPlugin, thicknessPlugin] as const
                        : [centerPlugin, thicknessPlugin] as const
                "
                class="chart"
            />
            <NoData v-else />
        </div>
        <div :id="containerID" />
    </div>
</template>

<script setup lang="ts">
    import {computed, PropType, watch} from "vue";
    import type {TooltipItem, ChartEvent, ActiveElement, Chart as ChartJS} from "chart.js";

    import {Chart, getDashboard} from "../composables/useDashboards";
    import {useChartGenerator} from "../composables/useDashboards";

    
    import NoData from "../../layout/NoData.vue";
    import Utils, {useTheme} from "../../../utils/utils";

    import {Doughnut, Pie} from "vue-chartjs";

    import {defaultConfig, getConsistentHEXColor, chartClick} from "../composables/charts";
    import {totalsDurationLegend, totalsLegend} from "../composables/useLegend";

    import moment from "moment";

    import {useRoute, useRouter} from "vue-router";
    import {FilterObject} from "../../../utils/filters";

    const route = useRoute();
    const router = useRouter();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });


    const containerID = `${props.chart.id}__${Math.random()}`;

    const {chartOptions} = props.chart;

    const columns = props.chart.data?.columns ?? {};
    const isDuration = Object.values(columns).find((c: Record<string, any>) => c.agg !== undefined)?.field === "DURATION";

    const theme = useTheme();

    const options = computed(() => {
        return defaultConfig({
            plugins: {
                ...(chartOptions?.legend?.enabled
                    ? {
                        totalsLegend: {
                            containerID,
                        },
                    }
                    : {}),
                tooltip: {
                    enabled: true,
                    intersect: true,
                    filter: (value: TooltipItem<"pie" | "doughnut">) => value.raw,
                    callbacks: {
                        label: (value: TooltipItem<"pie" | "doughnut">) => {
                            return `${isDuration ? Utils.humanDuration(value.raw as number) : value.raw}`;
                        },
                    }
                },
            },
            onClick: (_e: ChartEvent, elements: ActiveElement[]) => {
                chartClick(moment, router, route, {}, parsedData.value, elements, "dataset");
            },
        }, theme.value);
    });

    const centerPlugin = computed(() => ({
        id: "centerPlugin",
        beforeDraw(chart: ChartJS) {
            const darkTheme = theme.value === "dark";

            const ctx = chart.ctx;
            const dataset = chart.data.datasets[0];

            let total: number | string = (dataset.data as number[]).reduce((acc: number, val: number) => acc + val, 0);
            if (isDuration) {
                total = Utils.humanDuration(total);
            }

            const centerX = chart.width / 2;
            const centerY = chart.height / 2;

            ctx.save();
            ctx.font = "700 16px Public Sans";
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillStyle = darkTheme ? "#FFFFFF" : "#000000";

            ctx.fillText(String(total), centerX, centerY);

            ctx.restore();
        },
    }));

    const thicknessPlugin = {
        id: "thicknessPlugin",
        beforeDatasetsDraw(chart: ChartJS) {
            const {ctx} = chart;
            const dataset = chart.data.datasets[0] as any;
            const meta = chart.getDatasetMeta(0);

            //dynamically calculate thickness based on chart size
            const chartArea = chart.chartArea;
            if (!chartArea || !meta || !meta.data) return;
            // Available radius = half of the smaller dimension (width or height)
            const availableRadius = Math.min(chartArea.width, chartArea.height) / 2;
            // define thickness bounds relative to available radius 
            const minThicknessPx = Math.max(6, availableRadius * 0.05); // >0
            const maxThicknessPx = Math.max(12, availableRadius * 0.3);  // >0 
            // Reading weights from dataset with fallback weight(1)
            const weights: number[] = (dataset.thicknessWeight && Array.isArray(dataset.thicknessWeight))? dataset.thicknessWeight.map((w: any) => 
            {
                const n = Number(w);
                return Number.isFinite(n) ? Math.min(Math.max(n, 0), 1) : 1;
            })
                : meta.data.map(() => 1);
            for (let i = 0; i < meta.data.length; i++) {
                const arc = meta.data[i] as any;
                const w = weights[i] ?? 1;
                const thicknessPx = minThicknessPx + w * (maxThicknessPx - minThicknessPx);

                const baseRadius = arc.innerRadius ?? Math.max(0, availableRadius - thicknessPx);
                arc.outerRadius = baseRadius + thicknessPx;
                arc.innerRadius = baseRadius;

                arc.draw(ctx);
            }
        },
    };

    const parsedData = computed(() => {
        const parseValue = (value: unknown): string => {
            const date = moment(value as moment.MomentInput, moment.ISO_8601, true);
            return date.isValid() ? date.format("YYYY-MM-DD") : String(value);
        };
        const aggregator = Object.entries(columns).reduce<{
            value?: { label: string; key: string };
            field?: { label: string; key: string };
        }>(
            (result, [key, column]) => {
                const col = column as Record<string, any>;
                const type = "agg" in col ? "value" : "field";
                result[type] = {
                    label: col.displayName ?? col.agg,
                    key,
                };
                return result;
            },
            {},
        );

        const results: Record<string, number> = Object.create(null);

        const rawData = generated.value.results as Record<string, any>[] | undefined;
        rawData?.forEach((value: Record<string, any>) => {
            const field = parseValue(value[aggregator.field?.key ?? ""]);
            const aggregated = value[aggregator.value?.key ?? ""] as number;

            results[field] = (results[field] || 0) + aggregated;
        });

        const labels = Object.keys(results);
        const dataElements = labels.map((label) => results[label]);

        const backgroundColor = labels.map((label) => getConsistentHEXColor(theme.value, label));

        const maxDataValue = Math.max(...dataElements);
        const thicknessScale = dataElements.map(
            (value) => 21 + (value / maxDataValue) * 28,
        );

        return {
            labels,
            datasets: [
                {
                    data: dataElements,
                    backgroundColor,
                    thicknessScale,
                    borderWidth: 0,
                },
            ],
        };
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
    height: 100% !important;
    width: 100% !important;
    }
</style>
