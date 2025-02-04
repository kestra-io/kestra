<template>
    <div :id="containerID" />
    <Bar
        v-if="generated !== undefined"
        :data="parsedData"
        :options
        :plugins="chartOptions.legend.enabled ? [customBarLegend] : []"
        class="chart"
        :class="chartOptions.legend.enabled ? 'with-legend' : ''"
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
    import KestraUtils from "../../../../../utils/utils.js"

    const store = useStore();

    const dashboard = computed(() => store.state.dashboard.dashboard);

    const route = useRoute();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        identifier: {type: Number, required: true},
        chart: {type: Object, required: true},
        isPreview: {type: Boolean, required: false, default: false}
    });

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
                    ...DEFAULTS
                },
                y: {
                    title: {
                        display: true,
                        text: aggregator[0][1].displayName ?? aggregator[0][0],
                    },
                    position: "left",
                    ...DEFAULTS,
                    ticks: {
                        ...DEFAULTS.ticks,
                        callback: value => isDuration(aggregator[0][1].field) ? Utils.humanDuration(value) : value
                    }
                },
                ...(yBShown && {
                    yB: {
                        title: {
                            display: true,
                            text: aggregator[1][1].displayName ?? aggregator[1][0],
                        },
                        position: "right",
                        ...DEFAULTS,
                        display: true,
                        ticks: {
                            ...DEFAULTS.ticks,
                            callback: value => isDuration(aggregator[1][1].field) ? Utils.humanDuration(value) : value
                        }
                    },
                }),
            },
        });
    });

    function isDuration(field) {
        return field === "DURATION";
    }

    const parsedData = computed(() => {
        const parseValue = (value) => {
            const date = moment(value, moment.ISO_8601, true);
            return date.isValid() ? date.format(KestraUtils.getDateFormat(route.query.startDate, route.query.endDate)) : value;
        };

        const rawData = generated.value.results;
        const xAxis = (() => {
            const values = rawData.map((v) => {
                return parseValue(v[chartOptions.column]);
            });

            return Array.from(new Set(values)).sort();
        })();

        const aggregatorKeys = aggregator.map(([key]) => key);

        const reducer = (array, field, yAxisID) => {
            if (!array.length) return;

            const {columns} = data;
            const {column, colorByColumn} = chartOptions;

            // Get the fields for stacks (columns without `agg` and not the xAxis column)
            const fields = Object.keys(columns)
                .filter(key => !aggregatorKeys.includes(key))
                .filter(key => key !== column);

            return array.reduce((acc, {...params}) => {
                const stack = `(${fields.map(field => params[field]).join(", ")}): ${aggregator.map(agg => agg[0] + " = " + (isDuration(agg[1].field) ? Utils.humanDuration(params[agg[0]]) : params[agg[0]])).join(", ")}`;

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

        const yDataset = reducer(rawData, aggregator[0][0], "y");
        const yDatasetData = Object.values(getData(aggregator[0][0], yDataset));

        const label =
            aggregator?.[1]?.[1]?.displayName ?? aggregator?.[1]?.[1]?.field;

        return {
            labels: xAxis,
            datasets: yBShown
                ? [
                    {
                        yAxisID: "yB",
                        type: "line",
                        data: rawData.map((v) => v[aggregator[1][0]]),
                        fill: false,
                        pointRadius: 0,
                        borderWidth: 0.75,
                        label: label,
                        borderColor: getConsistentHEXColor(label),
                    },
                    ...yDatasetData,
                ]
                : yDatasetData,
        };
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
        } else {
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

