<template>
    <div :id="chart.id" class="float-end" />
    <Bar
        :data="parsedData"
        :options="options"
        :plugins="[barLegend]"
        class="chart"
    />
</template>

<script lang="ts" setup>
    import {onMounted, ref, computed} from "vue";

    import moment from "moment";
    import {Bar} from "vue-chartjs";

    import {barLegend} from "../legend.js";
    import {
        defaultConfig,
        getRandomHEXColor,
    } from "../../../../../utils/charts.js";

    defineOptions({inheritAttrs: false});
    const props = defineProps({chart: {type: Object, required: true}});

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
                barLegend: {containerID: props.chart.id, uppercase: true},
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

            return array.reduce((acc, {...params}) => {
                const label = params[chartOptions.colorByColumn];

                if (!acc[label]) {
                    acc[label] = {
                        type: "bar",
                        yAxisID,
                        data: [],
                        tooltip: label,
                        label,
                        unique: new Set(),
                        backgroundColor: getRandomHEXColor(),
                        ...params,
                    };
                }

                const current = acc[label];
                const parsed = parseValue(params[chartOptions.column]);

                if (!current.unique.has(parsed)) {
                    current.unique.add(parsed);
                    current.data.push({
                        date: parsed,
                        label: parsed,
                        [field]: params[field],
                    });
                } else {
                    const existing = current.data.find((v) => v.label === parsed);
                    if (existing) existing[field] += params[field];
                }

                return acc;
            }, {});
        };

        const getData = (field, object = {}) => {
            return Object.values(object).map((dataset) => {
                const data = xAxis.map((xAxisLabel) => {
                    const temp = dataset.data.find((v) => v.date === xAxisLabel);
                    return temp ? temp[field] : 0;
                });

                return {...dataset, data};
            });
        };

        const yDataset = reducer(generated.value, aggregator[0][0], "y");
        const yDatasetData = Object.values(getData(aggregator[0][0], yDataset));

        return {
            labels: xAxis,
            datasets: yBShown
                ? [
                    {
                        yAxisID: "yB",
                        type: "line",
                        data: generated.value.map((v) => v[aggregator[1][0]]),
                        tooltip: `${aggregator[1][1].displayName}`,
                        fill: false,
                        pointRadius: 0,
                        borderWidth: 0.75,
                        borderColor: getRandomHEXColor(),
                    },
                    ...yDatasetData,
                ]
                : yDatasetData,
        };
    });

    const generated = ref([]);
    onMounted(() => {
        // TODO: Fetch proper data from server and assign it to the variable below
        generated.value = [
            {
                date: "2024-11-20T10:27:00.000+01:00",
                duration: 0.488853444,
                country: "FR",
                total: 1,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T10:25:00.000+01:00",
                duration: 121.421243956,
                country: "IT",
                total: 1,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T10:24:00.000+01:00",
                duration: 0.88864897,
                country: "FR",
                total: 1,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T10:33:00.000+01:00",
                duration: 3.34020634,
                country: "FR",
                total: 4,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T10:31:00.000+01:00",
                duration: 0.486147859,
                country: "FR",
                total: 1,
                state: "SUCCESS",
            },
            {
                date: "2024-11-19T15:15:00.000+01:00",
                duration: 15.765342,
                country: "US",
                total: 3,
                state: "SUCCESS",
            },
            {
                date: "2024-11-18T08:45:00.000+01:00",
                duration: 2.456345,
                country: "JP",
                total: 2,
                state: "FAILURE",
            },
            {
                date: "2024-11-20T12:00:00.000+01:00",
                duration: 0.123456,
                country: "IT",
                total: 1,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T09:15:00.000+01:00",
                duration: 12.0345,
                country: "DE",
                total: 5,
                state: "SUCCESS",
            },
            {
                date: "2024-11-19T14:00:00.000+01:00",
                duration: 50.5,
                country: "US",
                total: 6,
                state: "FAILURE",
            },
            {
                date: "2024-11-17T17:30:00.000+01:00",
                duration: 0.987654,
                country: "JP",
                total: 2,
                state: "SUCCESS",
            },
            {
                date: "2024-11-15T10:05:00.000+01:00",
                duration: 75.345123,
                country: "JP",
                total: 3,
                state: "FAILURE",
            },
            {
                date: "2024-11-15T11:05:00.000+01:00",
                duration: 75.345123,
                country: "US",
                total: 3,
                state: "CREATED",
            },
            {
                date: "2024-11-16T19:40:00.000+01:00",
                duration: 3.141592,
                country: "JP",
                total: 1,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T14:20:00.000+01:00",
                duration: 1.2345,
                country: "JP",
                total: 4,
                state: "SUCCESS",
            },
            {
                date: "2024-11-18T21:50:00.000+01:00",
                duration: 22.222,
                country: "FR",
                total: 2,
                state: "FAILURE",
            },
            {
                date: "2024-11-14T11:25:00.000+01:00",
                duration: 8.765432,
                country: "FR",
                total: 5,
                state: "SUCCESS",
            },
            {
                date: "2024-11-19T16:35:00.000+01:00",
                duration: 10.54321,
                country: "US",
                total: 3,
                state: "FAILURE",
            },
            {
                date: "2024-11-18T05:45:00.000+01:00",
                duration: 0.654321,
                country: "IT",
                total: 2,
                state: "SUCCESS",
            },
            {
                date: "2024-11-20T13:00:00.000+01:00",
                duration: 4.56789,
                country: "FR",
                total: 7,
                state: "SUCCESS",
            },
            {
                date: "2024-11-16T18:45:00.000+01:00",
                duration: 33.3,
                country: "US",
                total: 4,
                state: "FAILURE",
            },
        ];
    });
</script>

<style lang="scss" scoped>
$height: 200px;

.chart {
    max-height: $height;
}
</style>
ss
