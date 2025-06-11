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

<script lang="ts" setup>
    import {computed,PropType} from "vue";

    import type {Chart} from "../composables/useDashboards";
    import {useChartGenerator} from "../composables/useDashboards";

    
    import NoData from "../../layout/NoData.vue";
    import Utils, {useTheme} from "../../../utils/utils";

    import {Doughnut, Pie} from "vue-chartjs";

    import {defaultConfig, getConsistentHEXColor, chartClick} from "../composables/charts.js";
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

            const thicknessScale = dataset.thicknessScale;

            meta.data.forEach((arc, index) => {
                const baseRadius = arc.innerRadius;
                const additionalThickness = thicknessScale[index];
                arc.outerRadius = baseRadius + additionalThickness;
                arc.innerRadius = baseRadius;

                arc.draw(ctx);
            });
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

        generated.value.results.forEach((value) => {
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

    const {data: generated} = useChartGenerator(props);
</script>

<style lang="scss" scoped>
    $height: 200px;

    .chart {
        max-height: $height;
    }
</style>