<template>
    <div :id="containerID" />
    <Bar
        v-if="generated !== undefined"
        :data="parsedData"
        :options="options"
        :plugins="chartOptions.legend.enabled ? [customBarLegend] : []"
        class="chart"
    />
    <NoData v-else />
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue";

    import NoData from "../../../../layout/NoData.vue";

    import {Bar} from "vue-chartjs";

    import {customBarLegend} from "../legend.js";
    import {defaultConfig, getConsistentHEXColor,} from "../../../../../utils/charts.js";

    import {useStore} from "vuex";
    import moment from "moment";

    import {useRoute} from "vue-router";
    import {Utils} from "@kestra-io/ui-libs";

    const store = useStore();

    const dashboard = computed(() => store.state.dashboard.dashboard);

    const route = useRoute();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        identifier: {type: Number, required: true},
        chart: {type: Object, required: true},
        isPreview: {type: Boolean, required: false, default: false}
    });

    const {data, chartOptions} = props.chart;

    const containerID = `${props.chart.id}__${Math.random()}`;

    const DEFAULTS = {
        display: true,
        stacked: true,
        ticks: {maxTicksLimit: 8},
        grid: {display: false},
    };

    const aggregator = Object.entries(data.columns).filter(([_, v]) => v.agg);

    const options = computed(() => {
        return defaultConfig({
            skipNull: true,
            barThickness: 12,
            borderSkipped: false,
            borderColor: "transparent",
            borderWidth: 2,
            plugins: {
                ...(chartOptions.legend.enabled
                    ? {
                        customBarLegend: {
                            containerID,
                            uppercase: true,
                        },
                    }
                    : {}),
                tooltip: {
                    enabled: true,
                    filter: (value) => value.raw,
                    callbacks: {
                        label: (value) => {
                            if (!value.dataset.tooltip) return "";
                            return `${value.dataset.tooltip}`;
                        },
                    },
                },
            },
            scales: {
                x: {
                    title: {
                        display: true,
                        text: data.columns[chartOptions.column].displayName ?? chartOptions.column,
                    },
                    position: "bottom",
                    ...DEFAULTS,
                },
                y: {
                    title: {
                        display: true,
                        text: aggregator[0][1].displayName ?? aggregator[0][0],
                    },
                    beginAtZero: true,
                    position: "left",
                    ...DEFAULTS,
                    ticks: {
                        ...DEFAULTS.ticks,
                        callback: value => isDurationAgg() ? Utils.humanDuration(value) : value
                    }
                },
            },
        });
    });

    function isDurationAgg() {
        return aggregator[0][1].field === "DURATION";
    }

    const parsedData = computed(() => {
        const column = chartOptions.column;
        const {columns} = data;

        // Ignore columns with `agg` and dynamically fetch valid ones
        const validColumns = Object.entries(columns)
            .filter(([_, value]) => !value.agg)
            .filter(c => c[0] !== column)// Exclude columns with `agg`
            .map(([key]) => key);

        const grouped = {};

        const rawData = generated.value.results;
        rawData.forEach((item) => {
            const key = validColumns.map((col) => item[col]).join(", "); // Use '|' as a delimiter

            if (!grouped[item[column]]) {
                grouped[item[column]] = {};
            }
            if (!grouped[item[column]][key]) {
                grouped[item[column]][key] = 0;
            }

            grouped[item[column]][key] += item[aggregator[0][0]];
        });

        const labels = Object.keys(grouped);
        const xLabels = [...new Set(rawData.map((item) => item[column]))];

        const datasets = xLabels.flatMap((xLabel) => {
            return Object.entries(grouped[xLabel]).map(subSectionsEntry => ({
                label: subSectionsEntry[0],
                data: xLabels.map(label => xLabel === label ? subSectionsEntry[1] : 0),
                backgroundColor: getConsistentHEXColor(subSectionsEntry[0]),
                tooltip: `(${subSectionsEntry[0]}): ${aggregator[0][0]} = ${(isDurationAgg() ? Utils.humanDuration(subSectionsEntry[1]) : subSectionsEntry[1])}`,
            }));
        });

        return {labels, datasets};
    });

    const generated = ref();
    const generate = async () => {
        if (!props.isPreview) {
            const params = {
                id: dashboard.value.id,
                chartId: props.chart.id,
                startDate: route.query.timeRange
                    ? moment()
                        .subtract(
                            moment.duration(route.query.timeRange).as("milliseconds"),
                        )
                        .toISOString(true)
                    : route.query.startDate ||
                        moment()
                            .subtract(moment.duration("PT720H").as("milliseconds"))
                            .toISOString(true),
                endDate: route.query.timeRange
                    ? moment().toISOString(true)
                    : route.query.endDate || moment().toISOString(true),
            };
            if (route.query.namespace) {
                params.namespace = route.query.namespace;
            }
            if (route.query.labels) {
                params.labels = Object.fromEntries(route.query.labels.map(l => l.split(":")));
            }
            generated.value = await store.dispatch("dashboard/generate", params);
        }
        else {
            generated.value = await store.dispatch("dashboard/chartPreview", props.chart.content)
        }
    };

    watch(route, async () => await generate());
    watch(
        () => props.identifier,
        () => generate(),
    );
    onMounted(() => generate());
</script>

<style lang="scss" scoped>
    .chart {
        #{--chart-height}: 200px;

        &:not(.with-legend) {
            #{--chart-height}: 231px;
        }

        min-height: var(--chart-height);
        max-height: var(--chart-height);
    }
</style>
