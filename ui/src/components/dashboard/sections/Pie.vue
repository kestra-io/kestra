<template>
    <div
        class="d-flex flex-row align-items-center justify-content-center h-100"
    >
        <div>
            <component
                :is="chartOptions.graphStyle === 'PIE' ? Pie : Doughnut"
                v-if="generated !== undefined"
                :data="parsedData"
                :options="options"
                :plugins="
                    chartOptions?.legend?.enabled
                        ? [isDuration ? totalsDurationLegend : totalsLegend, centerPlugin, thicknessPlugin]
                        : [centerPlugin, thicknessPlugin]
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

    import {Chart, getDashboard} from "../composables/useDashboards";
    import {useChartGenerator} from "../composables/useDashboards";

    
    import NoData from "../../layout/NoData.vue";
    import Utils, {useTheme} from "../../../utils/utils";

    import {Doughnut, Pie} from "vue-chartjs";

    import {defaultConfig, getConsistentHEXColor, chartClick} from "../composables/charts";
    import {totalsDurationLegend, totalsLegend} from "../composables/useLegend";

    import moment from "moment";

    import {useRoute, useRouter} from "vue-router";

    const route = useRoute();
    const router = useRouter();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<string[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    });


    const containerID = `${props.chart.id}__${Math.random()}`;

    const {chartOptions} = props.chart;

    const isDuration = Object.values(props.chart.data.columns).find(c => c.agg !== undefined).field === "DURATION";

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
                    filter: (value) => value.raw,
                    callbacks: {
                        label: (value) => {
                            return `${isDuration ? Utils.humanDuration(value.raw) : value.raw}`;
                        },
                    }
                },
            },
            onClick: (e, elements) => {
                chartClick(moment, router, route, {}, parsedData.value, elements, "dataset");
            },
        }, theme.value);
    });

    const centerPlugin = computed(() => ({
        id: "centerPlugin",
        beforeDraw(chart) {
            const darkTheme = theme.value === "dark";

            const ctx = chart.ctx;
            const dataset = chart.data.datasets[0];

            let total = dataset.data.reduce((acc, val) => acc + val, 0);
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

            ctx.fillText(total, centerX, centerY);

            ctx.restore();
        },
    }));

    const thicknessPlugin = {
        id: "thicknessPlugin",
        beforeDatasetsDraw(chart) {
            const {ctx} = chart;
            const dataset = chart.data.datasets[0];
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
                const arc = meta.data[i];
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
        const parseValue = (value) => {
            const date = moment(value, moment.ISO_8601, true);
            return date.isValid() ? date.format("YYYY-MM-DD") : value;
        };
        const aggregator = Object.entries(props.chart.data.columns).reduce(
            (result, [key, column]) => {
                const type = "agg" in column ? "value" : "field";
                result[type] = {
                    label: column.displayName ?? column.agg,
                    key,
                };
                return result;
            },
            {},
        );

        let results = Object.create(null);

        generated.value.results?.forEach((value) => {
            const field = parseValue(value[aggregator.field.key]);
            const aggregated = value[aggregator.value.key];

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
