<template>
    <div :id="containerID" class="float-end" />
    <Bar
        v-if="generated.length"
        :data="parsedData"
        :options="options"
        :plugins="[customBarLegend]"
        class="chart"
    />
    <NoData v-else />
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue";

    import NoData from "../../../../layout/NoData.vue";

    import {Bar} from "vue-chartjs";

    import {customBarLegend} from "../legend.js";
    import {
        defaultConfig,
        getConsistentHEXColor,
    } from "../../../../../utils/charts.js";

    import {useStore} from "vuex";
    const store = useStore();

    const dashboard = computed(() => store.state.dashboard.dashboard);

    import moment from "moment";

    import {useRoute} from "vue-router";
    const route = useRoute();

    defineOptions({inheritAttrs: false});
    const props = defineProps({chart: {type: Object, required: true}});

    const {data, chartOptions} = props.chart;

    const containerID = `${props.chart.id}__${Math.random()}`;

    const DEFAULTS = {
        display: true,
        stacked: true,
        ticks: {maxTicksLimit: 8},
        grid: {display: false},
    };
    const options = computed(() => {
        return defaultConfig({
            skipNull: true,
            barThickness: 12,
            borderSkipped: false,
            borderColor: "transparent",
            borderWidth: 2,
            plugins: {
                customBarLegend: {containerID, uppercase: true},
                tooltip: {
                    enabled: true,
                    filter: (value) => value.raw,
                    callbacks: {
                        label: (value) => {
                            return `${value.dataset.tooltip} : ${value.raw}`;
                        },
                    },
                },
            },
            scales: {
                y: {
                    title: {
                        display: true,
                    },
                    beginAtZero: true,
                    position: "left",
                    ...DEFAULTS,
                },
            },
        });
    });

    const parsedData = computed(() => {
        const column = chartOptions.column;
        const {columns} = data;

        // Ignore columns with `agg` and dynamically fetch valid ones
        const validColumns = Object.entries(columns)
            .filter(([_, value]) => !value.agg) // Exclude columns with `agg`
            .map(([key]) => key);

        const grouped = {};

        const aggregator = Object.entries(data.columns).filter(([_, v]) => v.agg);

        generated.value.forEach((item) => {
            const key = validColumns.map((col) => item[col]).join("|"); // Use '|' as a delimiter

            if (!grouped[key]) {
                grouped[key] = {};
            }
            if (!grouped[key][item[column]]) {
                grouped[key][item[column]] = 0;
            }

            grouped[key][item[column]] += item[aggregator[0][0]];
        });

        const labels = Object.keys(grouped);
        const unique = [...new Set(generated.value.map((item) => item[column]))];

        const datasets = unique.map((value) => ({
            label: value,
            data: labels.map((label) => grouped[label][value] || 0),
            backgroundColor: getConsistentHEXColor(value),
            tooltip: aggregator[0][0],
        }));

        return {labels, datasets};
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
