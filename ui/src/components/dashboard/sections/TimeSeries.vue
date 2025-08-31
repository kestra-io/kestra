<template>
    <div :id="containerID" />
    <el-tooltip
        v-if="generated !== undefined"
        effect="light"
        placement="top"
        :persistent="false"
        :hide-after="0"
        :popper-class="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
        :content="tooltipContent"
        raw-content
    >
        <div>
            <Bar
                :data="parsedData"
                :options
                :plugins="chartOptions?.legend?.enabled ? [customBarLegend] : []"
                :class="props.short ? 'short-chart' : 'chart'"
                class="chart"
            />
        </div>
    </el-tooltip>
    <NoData v-else-if="!props.short" />
</template>

<script lang="ts" setup>
    import {computed, ref, watch, PropType} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import moment from "moment";
    import {Bar} from "vue-chartjs";
    import NoData from "../../layout/NoData.vue";
    import {Chart, getDashboard, useChartGenerator} from "../composables/useDashboards";
    import {customBarLegend} from "../composables/useLegend";
    import {defaultConfig, getConsistentHEXColor, chartClick, tooltip} from "../composables/charts.js";
    import {cssVariable, Utils} from "@kestra-io/ui-libs";
    import KestraUtils, {useTheme} from "../../../utils/utils";

    const route = useRoute();
    const router = useRouter();

    defineOptions({inheritAttrs: false});
    const props = defineProps({
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<string[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
        short: {type: Boolean, default: false},
    });


    const containerID = `${props.chart.id}__${Math.random()}`;
    const tooltipContent = ref("");

    const {data, chartOptions} = props.chart;

    const aggregator = computed(() => {
        return Object.entries(data.columns)
            .filter(([_, v]) => v.agg)
            .sort((a, b) => {
                const aStyle = a[1].graphStyle || "";
                const bStyle = b[1].graphStyle || "";
                return aStyle.localeCompare(bStyle);
            });
    });

    const yBShown = computed(() => aggregator.value.length === 2);

    const theme = useTheme();

    const DEFAULTS = {
        display: true,
        stacked: true,
        ticks: {maxTicksLimit: 8},
        grid: {display: false},
    };
    const options = computed(() => {
        return defaultConfig({
            skipNull: true,
            barThickness: props.short ? 8 : 12,
            maxBarThickness: props.short ? 8 : 12,
            categoryPercentage: props.short ? 1.0 : 0.8,
            barPercentage: props.short ? 1.0 : 0.9,
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
                    filter: (value) => value.raw,
                    callbacks: {
                        label: (value) => {
                            if (!value.dataset.tooltip) return "";
                            return `${value.dataset.tooltip}`;
                        },
                    },
                    external: (props.short) ? function (context) {
                        tooltipContent.value = tooltip(context.tooltip);
                    } : undefined,
                },
            },
            scales: {
                x: {
                    title: {
                        display: props.short ? false : true,
                        text: data.columns[chartOptions.column].displayName ?? chartOptions.column,
                    },
                    position: "bottom",
                    ...DEFAULTS,
                    display: props.short ? false : true,
                },
                y: {
                    title: {
                        display: props.short ? false : true,
                        text: aggregator.value[0]?.[1]?.displayName ?? aggregator.value[0]?.[0],
                    },
                    position: "left",
                    ...DEFAULTS,
                    display: props.short ? false : true,
                    ticks: {
                        ...DEFAULTS.ticks,
                        callback: (value: any) => isDuration(aggregator.value[0]?.[1]?.field) ? Utils.humanDuration(value) : value
                    }
                },
                ...(yBShown.value && {
                    yB: {
                        title: {
                            display: props.short ? false : true,
                            text: aggregator.value[1]?.[1]?.displayName ?? aggregator.value[1]?.[0],
                        },
                        position: "right",
                        ...DEFAULTS,
                        display: props.short ? false : true,
                        ticks: {
                            ...DEFAULTS.ticks,
                            callback: (value: any) => isDuration(aggregator.value[1]?.[1]?.field) ? Utils.humanDuration(value) : value
                        }
                    },
                }),
            },
            onClick: (e, elements) => {
                if (data.type === "io.kestra.plugin.core.dashboard.data.Logs") {
                    return;
                }
                chartClick(moment, router, route, {}, parsedData.value, elements, "label");
            },
        }, theme.value);
    });

    function isDuration(field) {
        return field === "DURATION";
    }

    const parseValue = (value) => {
        const date = moment(value, moment.ISO_8601, true);
        return date.isValid() ? date.format(KestraUtils.getDateFormat(route.query.startDate, route.query.endDate)) : value;
    };

    const parsedData = computed(() => {
        const rawData = generated.value.results;
        const xAxis = (() => {
            const values = rawData.map((v) => {
                return parseValue(v[chartOptions.column]);
            });

            return Array.from(new Set(values)).sort();
        })();

        const aggregatorKeys = aggregator.value.map(([key]) => key);

        const reducer = (array, field, yAxisID) => {
            if (!array.length) return;

            const {columns} = data;
            const {column, colorByColumn} = chartOptions;

            // Get the fields for stacks (columns without `agg` and not the xAxis column)
            const fields = Object.keys(columns)
                .filter(key => !aggregatorKeys.includes(key))
                .filter(key => key !== column);

            return array.reduce((acc: any, {...params}) => {
                const stack = `(${fields.map(field => params[field]).join(", ")}): ${aggregator.value.map(agg => agg[0] + " = " + (isDuration(agg[1].field) ? Utils.humanDuration(params[agg[0]]) : params[agg[0]])).join(", ")}`;

                if (!acc[stack]) {
                    acc[stack] = {
                        type: "bar",
                        yAxisID,
                        data: [],
                        tooltip: stack,
                        label: params[colorByColumn],
                        backgroundColor: getConsistentHEXColor(
                            theme.value,
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

        const yDataset = reducer(rawData, aggregator.value[0][0], "y");

        // Sorts the dataset array by the descending sum of 'data' values.
        // If two datasets have the same sum, it sorts them alphabetically by 'label'.
        const yDatasetData = Object.values(getData(aggregator.value[0][0], yDataset)).sort((a: any, b: any) => {
            const sumA = a.data.reduce((sum: number, val: number) => sum + val, 0);
            const sumB = b.data.reduce((sum: number, val: number) => sum + val, 0);

            if (sumB !== sumA) {
                return sumB - sumA; // Descending by sum
            }

            return a.label.localeCompare(b.label); // Ascending alphabetically by label
        });

        const label = aggregator.value?.[1]?.[1]?.displayName ?? aggregator.value?.[1]?.[1]?.field;

        let duration: number[] = [];
        if(yBShown.value){
            const helper = Array.from(new Set(rawData.map((v) => parseValue(v.date)))).sort();

            // Step 1: Group durations by formatted date
            const groupedDurations = {};
            rawData.forEach(item => {
                const formattedDate = parseValue(item.date);
                groupedDurations[formattedDate] = (groupedDurations[formattedDate] || 0) + item.duration;
            });

            // Step 2: Map to target dates
            duration = helper.map(date => groupedDurations[date] || 0);
        }

        return {
            labels: xAxis,
            datasets: yBShown.value
                ? [
                    {
                        yAxisID: "yB",
                        type: "line",
                        data: duration,
                        fill: false,
                        pointRadius: 0,
                        borderWidth: 0.75,
                        label: label,
                        borderColor: props.short ? cssVariable("--ks-background-running") : cssVariable("--ks-border-running")
                    },
                    ...yDatasetData,
                ]
                : yDatasetData,
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

<style lang="scss" scoped>
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
