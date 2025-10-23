<template>
    <el-tooltip
        effect="light"
        placement="left"
        :persistent="false"
        :hideAfter="0"
        transition=""
        :popperClass="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
        :disabled="!externalTooltip"
        :content="tooltipContent"
        rawContent
    >
        <div>
            <Bar
                v-if="loading"
                :class="small ? 'small' : ''"
                :data="skeletonData"
                :options="skeletonOptions"
            />
            <Bar
                v-else
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

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import moment from "moment";
    import {Bar} from "vue-chartjs";
    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import Utils, {useTheme} from "../../utils/utils";
    import {useScheme} from "../../utils/scheme";
    import {defaultConfig, tooltip, getFormat, chartClick} from "../dashboard/composables/charts";

    import {State} from "@kestra-io/ui-libs";
    const ORDER = State.arrayAllStates().map((state) => state.name);

    const {t} = useI18n({useScope: "global"});

    interface DataItem {
        startDate: string;
        executionCounts: Record<string, number>;
        duration: { avg: number | string };
        groupBy?: string;
    }

    const props = withDefaults(defineProps<{
        data: DataItem[];
        plugins?: any[];
        total?: number;
        duration?: boolean;
        scales?: boolean;
        small?: boolean;
        externalTooltip?: boolean;
        loading?: boolean;
    }>(), {
        plugins: () => [],
        total: undefined,
        duration: true,
        scales: true,
        small: false,
        externalTooltip: false,
        loading: false
    });

    const theme = useTheme();
    const scheme = useScheme();
    const tooltipContent = ref<string | undefined>("");

    const skeletonData = computed(() => {
        const barColor = theme.value === "dark"
            ? "rgba(255, 255, 255, 0.08)"
            : "rgba(0, 0, 0, 0.06)";

        return {
            labels: Array(18).fill(""),
            datasets: [{
                data: Array(18).fill(0).map(() => Math.random() * 70 + 30),
                backgroundColor: barColor,
                borderRadius: 2,
                barThickness: props.small ? 8 : 12,
            }]
        };
    });

    const skeletonOptions = computed(() => ({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                display: false
            },
            tooltip: {
                enabled: false
            }
        },
        scales: {
            x: {
                display: false,
                grid: {
                    display: false
                }
            },
            y: {
                display: false,
                grid: {
                    display: false
                },
                min: 0,
                max: 100
            }
        }
    }));

    const parsedData = computed<any>(() => {
        const datasets = props.data.reduce(function (accumulator: Record<string, {
            label: string
            backgroundColor: string
            yAxisID: string
            data: number[]
        }>, value) {
            Object.keys(value.executionCounts).forEach(function (state) {
                if (accumulator[state] === undefined) {
                    accumulator[state] = {
                        label: state,
                        backgroundColor: scheme.value[state as keyof typeof scheme.value] || "#000000",
                        yAxisID: "y",
                        data: [],
                    };
                }

                accumulator[state].data.push(value.executionCounts[state]);
            });

            return accumulator;
        }, Object.create(null));

        const datasetsArray = Object.values(datasets).sort((a, b) => {
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
                            return value.duration.avg === 0 || !value.duration.avg
                                ? 0
                                : Utils.duration(String(value.duration.avg));
                        }),
                    },
                    ...datasetsArray,
                ]
                : datasetsArray,
        };
    });

    const options = computed<any>(() =>
        defaultConfig({
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
                    filter: (value: any) => value.raw,
                    callbacks: {
                        label: function (value: any) {
                            const {label, yAxisID} = value.dataset;
                            return `${label.toLowerCase().capitalize()}: ${value.raw}${yAxisID === "yB" ? "s" : ""}`;
                        },
                    },
                    external: props.externalTooltip ? function (context: any) {
                        let content = tooltip(context.tooltip);
                        tooltipContent.value = content;
                    } : undefined,
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
                        callback: function (this: any, value: any) {
                            const label = this.getLabelForValue(value);

                            if (
                                moment(label, ["h:mm A", "HH:mm"], true).isValid()
                            ) {
                                // Handle time strings like "1:15 PM" or "13:15"
                                return moment(label, ["h:mm A", "HH:mm"]).format(
                                    "h:mm A",
                                );
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
                        callback: function (this: any, value: any) {
                            return `${this.getLabelForValue(value)}s`;
                        },
                    },
                },
            },
            onClick: (_e: any, elements: any) => {
                chartClick(moment, router, route, {}, parsedData.value, elements, "label");
            },
        }, theme.value),
    );
</script>

<style scoped lang="scss">
.small {
    height: 40px;
}
</style>