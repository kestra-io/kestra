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

    const containerID = `${props.chart.id}__${Math.random()}`;

    const {data, chartOptions} = props.chart;

    const aggregator = Object.entries(data.columns)
        .filter(([_, v]) => v.agg)
        .sort((a, b) => a[1].graphStyle.localeCompare(b[1].graphStyle));
    const yBShown = aggregator.length === 2;

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
                x: {
                    title: {
                        display: true,
                        text: data.columns[chartOptions.column].displayName,
                    },
                    position: "bottom",
                    ...DEFAULTS,
                },
                y: {
                    title: {
                        display: true,
                        text: aggregator[0][1].displayName,
                    },
                    position: "left",
                    ...DEFAULTS,
                },
                ...(yBShown && {
                    yB: {
                        title: {
                            display: true,
                            text: aggregator[1][1].displayName,
                        },
                        position: "right",
                        ...DEFAULTS,
                        display: true,
                    },
                }),
            },
        });
    });

    const parsedData = computed(() => {
        const parseValue = (value) => {
            const date = moment(value, moment.ISO_8601, true);
            return date.isValid() ? date.format("YYYY-MM-DD") : value;
        };

        const xAxis = (() => {
            const values = generated.value.map((v) => {
                return parseValue(v[chartOptions.column]);
            });

            return Array.from(new Set(values)).sort();
        })();

        const reducer = (array, field, yAxisID) => {
            if (!array.length) return;

            const {columns} = data;
            const {column, colorByColumn} = chartOptions;

            // Get the fields for stacks (columns without `agg` and not the xAxis column)
            const fields = Object.entries(columns)
                .filter(([key, value]) => !value.agg && key !== column)
                .map(([key]) => key);

            return array.reduce((acc, {...params}) => {
                const stack = fields.map((f) => `${f}: ${params[f]}`).join(", ");

                if (!acc[stack]) {
                    acc[stack] = {
                        type: "bar",
                        yAxisID,
                        data: [],
                        tooltip: stack,
                        label: params[colorByColumn],
                        backgroundColor: getConsistentHEXColor(
                            params[colorByColumn],
                        ),
                        unique: new Set(),
                    };
                }

                const current = acc[stack];
                const parsedDate = parseValue(params[column]);

                // Check if the date is already processed
                if (!current.unique.has(parsedDate)) {
                    current.unique.add(parsedDate);
                    current.data.push({
                        x: parsedDate,
                        y: params[field],
                    });
                } else {
                    // Update existing stack value for the same date
                    const existing = current.data.find((v) => v.x === parsedDate);
                    if (existing) existing.y += params[field];
                }

                return acc;
            }, {});
        };

        const getData = (field, object = {}) => {
            return Object.values(object).map((dataset) => {
                const data = xAxis.map((xAxisLabel) => {
                    const temp = dataset.data.find((v) => v.x === xAxisLabel);
                    return temp ? temp.y : 0;
                });

                return {...dataset, data};
            });
        };

        const yDataset = reducer(generated.value, aggregator[0][0], "y");
        const yDatasetData = Object.values(getData(aggregator[0][0], yDataset));

        const label = aggregator[1][1].displayName ?? aggregator[1][1].field;

        return {
            labels: xAxis,
            datasets: yBShown
                ? [
                    {
                        yAxisID: "yB",
                        type: "line",
                        data: generated.value.map((v) => v[aggregator[1][0]]),
                        tooltip: label,
                        fill: false,
                        pointRadius: 0,
                        borderWidth: 0.75,
                        borderColor: getConsistentHEXColor(label),
                    },
                    ...yDatasetData,
                ]
                : yDatasetData,
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
