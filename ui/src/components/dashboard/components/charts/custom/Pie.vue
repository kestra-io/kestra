<template>
    <div
        class="d-flex flex-row align-items-center justify-content-center h-100"
    >
        <div class="w-75">
            <component
                :is="chartOptions.graphStyle === 'PIE' ? Pie : Doughnut"
                v-if="generated.length"
                :data="parsedData"
                :options="options"
                :plugins="[totalsLegend, centerPlugin, thicknessPlugin]"
                class="chart"
            />
            <NoData v-else />
        </div>
        <div :id="containerID" />
    </div>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue";

    import NoData from "../../../../layout/NoData.vue";
    import Utils from "../../../../../utils/utils.js";

    import {Doughnut, Pie} from "vue-chartjs";

    import {
        defaultConfig,
        getConsistentHEXColor,
    } from "../../../../../utils/charts.js";
    import {totalsLegend} from "../legend.js";

    import moment from "moment";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useStore} from "vuex";
    const store = useStore();

    const dashboard = computed(() => store.state.dashboard.dashboard);

    defineOptions({inheritAttrs: false});
    const props = defineProps({chart: {type: Object, required: true}});

    const containerID = `${props.chart.id}__${Math.random()}`;

    const {chartOptions} = props.chart;

    const options = computed(() =>
        defaultConfig({
            plugins: {
                totalsLegend: {containerID},
                tooltip: {
                    enabled: true,
                    intersect: true,
                    filter: (value) => value.raw,
                    callbacks: {
                        title: () => "",
                        label: (value) => {
                            return `${value.dataset.tooltip} : ${value.raw}`;
                        },
                    },
                },
            },
        }),
    );

    const centerPlugin = {
        id: "centerPlugin",
        beforeDraw(chart) {
            const darkTheme = Utils.getTheme() === "dark";

            const ctx = chart.ctx;
            const dataset = chart.data.datasets[0];
            const total = dataset.data.reduce((acc, val) => acc + val, 0);
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
    };

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
                    label: column.displayName,
                    key,
                };
                return result;
            },
            {},
        );

        let results = Object.create(null);

        generated.value.forEach((value) => {
            const field = parseValue(value[aggregator.field.key]);
            const aggregated = value[aggregator.value.key];

            results[field] = (results[field] || 0) + aggregated;
        });

        const labels = Object.keys(results);
        const dataElements = labels.map((label) => results[label]);

        const backgroundColor = labels.map((label) => getConsistentHEXColor(label));

        const maxDataValue = Math.max(...dataElements);
        const thicknessScale = dataElements.map(
            (value) => 21 + (value / maxDataValue) * 28,
        );

        return {
            labels,
            datasets: [
                {
                    data: dataElements,
                    tooltip: aggregator.value.label,
                    backgroundColor,
                    thicknessScale,
                    borderWidth: 0,
                },
            ],
        };
    });

    const generated = ref([]);
    onMounted(async () => {
        generated.value = await store.dispatch("dashboard/generate", {
            id: dashboard.value.id,
            chartId: props.chart.id,
            startDate:
                route.query.startDate ??
                moment()
                    .subtract(moment.duration("PT720H").as("milliseconds"))
                    .toISOString(true),
            endDate: route.query.endDate ?? moment().toISOString(true),
        });
    });
</script>

<style lang="scss" scoped>
$height: 200px;

.chart {
    max-height: $height;
}
</style>
ss
