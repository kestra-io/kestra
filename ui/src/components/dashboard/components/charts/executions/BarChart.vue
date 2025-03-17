<template>
    <el-tooltip
        effect="light"
        placement="left"
        :persistent="false"
        :hide-after="0"
        transition=""
        :popper-class="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
        :disabled="!externalTooltip"
        :content="tooltipContent"
        raw-content
    >
        <div>
            <Bar
                :class="small ? 'small' : ''"
                :data="parsedData"
                :options="options"
                :total="total"
                :plugins="plugins"
                :duration="duration"
            />
        </div>
    </el-tooltip>
</template>

<script setup>
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import moment from "moment";
    import {Bar} from "vue-chartjs";
    import {useRouter} from "vue-router";
    const router = useRouter();

    import Utils, {useTheme} from "../../../../../utils/utils.js";

    import {
        defaultConfig,
        tooltip,
        getFormat,
    } from "../../../../../utils/charts.js";

    import {State} from "@kestra-io/ui-libs";
    const ORDER = State.arrayAllStates().map((state) => state.name);

    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        data: {
            type: Object,
            required: true,
        },
        plugins: {
            type: Array,
            default: () => [],
        },
        total: {
            type: Number,
            default: undefined,
        },
        duration: {
            type: Boolean,
            default: true,
        },
        scales: {
            type: Boolean,
            default: true,
        },
        small: {
            type: Boolean,
            default: false,
        },
        externalTooltip: {
            type: Boolean,
            default: false,
        },
    });

    const theme = useTheme();

    const tooltipContent = ref("");

    const parsedData = computed(() => {
        function getRandomData(length, min = 0, max = 4) {
            return Array.from(
                {length},
                () => Math.floor(Math.random() * (max - min + 1)) + min,
            );
        }

        let datasets = {
            RUNNING: {
                label: "RUNNING",
                backgroundColor: "#5bb8ff",
                yAxisID: "y",
                data: getRandomData(30),
            },
            RESTARTED: {
                label: "RESTARTED",
                backgroundColor: "#c7f0ff",
                yAxisID: "y",
                data: getRandomData(30),
            },
            WARNING: {
                label: "WARNING",
                backgroundColor: "#eeae7e",
                yAxisID: "y",
                data: getRandomData(30),
            },
            KILLING: {
                label: "KILLING",
                backgroundColor: "#a6a4ca",
                yAxisID: "y",
                data: getRandomData(30),
            },
            SUCCESS: {
                label: "SUCCESS",
                backgroundColor: "#21ce9c",
                yAxisID: "y",
                data: getRandomData(30),
            },
            PAUSED: {
                label: "PAUSED",
                backgroundColor: "#fde89d",
                yAxisID: "y",
                data: getRandomData(30),
            },
            FAILED: {
                label: "FAILED",
                backgroundColor: "#fd7278",
                yAxisID: "y",
                data: getRandomData(30),
            },
            QUEUED: {
                label: "QUEUED",
                backgroundColor: "#bda85d",
                yAxisID: "y",
                data: getRandomData(30),
            },
            KILLED: {
                label: "KILLED",
                backgroundColor: "#7e719f",
                yAxisID: "y",
                data: getRandomData(30),
            },
            CREATED: {
                label: "CREATED",
                backgroundColor: "#fd9297",
                yAxisID: "y",
                data: getRandomData(30),
            },
            CANCELLED: {
                label: "CANCELLED",
                backgroundColor: "#9a8eb4",
                yAxisID: "y",
                data: getRandomData(30),
            },
        };

        datasets = Object.values(datasets).sort((a, b) => {
            return ORDER.indexOf(a.label) - ORDER.indexOf(b.label);
        });

        return {
            labels: props.data.map((r) =>
                moment(r.startDate).format(getFormat(r.groupBy)),
            ),
            datasets: props.duration
                ? [
                    {
                        type: "line",
                        label: t("duration"),
                        fill: false,
                        pointRadius: 0,
                        borderWidth: 0.75,
                        borderColor: "#A2CDFF",
                        yAxisID: "yB",
                        data: props.data.map((value) => {
                            return value.duration.avg === 0
                                ? 0
                                : Utils.duration(value.duration.avg);
                        }),
                    },
                    ...Object.values(datasets),
                ]
                : Object.values(datasets),
        };
    });

    const options = computed(() =>
        defaultConfig(
            {
                barThickness: props.small ? 8 : 12,
                skipNull: true,
                borderSkipped: false,
                borderColor: "transparent",
                borderWidth: 2,
                plugins: {
                    barLegend: {
                        containerID: "executions",
                    },
                    tooltip: {
                        enabled: !props.externalTooltip,
                        filter: (value) => value.raw,
                        callbacks: {
                            label: function (value) {
                                const {label, yAxisID} = value.dataset;
                                return `${label.toLowerCase().capitalize()}: ${value.raw}${yAxisID === "yB" ? "s" : ""}`;
                            },
                        },
                        external: props.externalTooltip
                            ? function (context) {
                                let content = tooltip(context.tooltip);
                                tooltipContent.value = content;
                            }
                            : undefined,
                    },
                },
                scales: {
                    x: {
                        display: props.scales,
                        title: {
                            display: true,
                            text: t("date"),
                        },
                        grid: {
                            display: false,
                        },
                        position: "bottom",
                        stacked: true,
                        ticks: {
                            maxTicksLimit: props.small ? 5 : 8,
                            callback: function (value) {
                                const label = this.getLabelForValue(value);

                                if (
                                    moment(
                                        label,
                                        ["h:mm A", "HH:mm"],
                                        true,
                                    ).isValid()
                                ) {
                                    // Handle time strings like "1:15 PM" or "13:15"
                                    return moment(label, [
                                        "h:mm A",
                                        "HH:mm",
                                    ]).format("h:mm A");
                                } else if (moment(new Date(label)).isValid()) {
                                    // Handle date strings
                                    const date = moment(new Date(label));
                                    const isCurrentYear =
                                        date.year() === moment().year();
                                    return date.format(
                                        isCurrentYear ? "MM/DD" : "MM/DD/YY",
                                    );
                                }

                                // Return the label as-is if it's neither a valid date nor time
                                return label;
                            },
                        },
                    },
                    y: {
                        display: props.scales,
                        title: {
                            display: !props.small,
                            text: t("executions"),
                        },
                        grid: {
                            display: false,
                        },
                        position: "left",
                        stacked: true,
                        ticks: {
                            maxTicksLimit: props.small ? 5 : 8,
                        },
                    },
                    yB: {
                        title: {
                            display: props.duration && !props.small,
                            text: t("duration"),
                        },
                        grid: {
                            display: false,
                        },
                        display: props.duration,
                        position: "right",
                        ticks: {
                            maxTicksLimit: props.small ? 5 : 8,
                            callback: function (value) {
                                return `${this.getLabelForValue(value)}s`;
                            },
                        },
                    },
                },
                onClick: (e, elements) => {
                    if (elements.length > 0) {
                        const state =
                            parsedData.value.datasets[elements[0].datasetIndex]
                                .label;
                        router.push({
                            name: "executions/list",
                            query: {
                                state: state,
                                scope: "USER",
                                size: 100,
                                page: 1,
                            },
                        });
                    }
                },
            },
            theme.value,
        ),
    );
</script>

<style>
.small {
    max-height: 40px;
}
</style>
