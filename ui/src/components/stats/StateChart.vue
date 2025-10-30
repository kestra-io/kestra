<template>
    <div :class="'executions-charts' + (global ? (big ? ' big' : '') : ' mini')" v-if="dataReady">
        <el-tooltip
            effect="light"
            :placement="(global ? 'bottom' : 'left')"
            :persistent="false"
            :hideAfter="0"
            transition=""
            :popperClass="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
        >
            <template #content>
                <span v-html="tooltipContent" />
            </template>
            <Bar ref="chartRef" :data="chartData" :options="options" />
        </el-tooltip>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useRoute, useRouter} from "vue-router"
    import {Bar} from "vue-chartjs";
    import Utils, {useTheme} from "../../utils/utils";
    import {useScheme} from "../../utils/scheme";
    import {defaultConfig, tooltip, chartClick, getFormat} from "../dashboard/composables/charts";
    import {useI18n} from "vue-i18n";
    import {getCurrentInstance} from "vue";

    interface ExecutionData {
        startDate: string;
        groupBy: string;
        executionCounts: Record<string, number>;
        duration: {
            avg: number;
        };
    }

    interface Dataset {
        type?: string;
        label: string;
        backgroundColor: string;
        yAxisID: string;
        data: number[];
        fill?: string;
        pointRadius?: number;
        borderWidth?: number;
        borderColor?: string;
    }

    interface ChartDataConfig {
        labels: string[];
        datasets: Dataset[];
    }

    interface Props {
        data: ExecutionData[];
        duration?: boolean;
        global?: boolean;
        big?: boolean;
        namespace?: string;
        flowId?: string;
    }

    const props = withDefaults(defineProps<Props>(), {
        duration: false,
        global: false,
        big: false,
        namespace: undefined,
        flowId: undefined,
    });

    const moment = getCurrentInstance()?.appContext.config.globalProperties?.$moment;
    const route = useRoute();
    const router = useRouter();
    const {t} = useI18n({useScope: "global"});

    const durationLabel = t("duration");
    const chartRef = ref();
    const tooltipContent = ref<string>("");

    const dataReady = computed(() => props.data.length > 0);
    const theme = useTheme();
    const scheme = useScheme();

    const darkTheme = computed(() => theme.value === "dark");

    const options = computed(() => defaultConfig({
        barThickness: 4,
        onClick: (e: any, elements: any[]) => {
            if (elements.length > 0 && elements[0].index !== undefined && elements[0].datasetIndex !== undefined) {
                chartClick(
                    moment,
                    router,
                    route,
                    {
                        date: e.chart.data.labels[elements[0].index],
                        state: e.chart.data.datasets[elements[0].datasetIndex].label,
                        namespace: props.namespace,
                        flowId: props.flowId
                    }
                )
            }
        },
        plugins: {
            tooltip: {
                external: function (context: any) {
                    const content = tooltip(context.tooltip);
                    tooltipContent.value = content;
                },
                callbacks: {
                    label: function (context: any) {
                        if (context.dataset.yAxisID === "yB" && context.raw !== 0) {
                            return context.dataset.label + ": " + Utils.humanDuration(context.raw);
                        } else if (context.formattedValue !== "0") {
                            return context.dataset.label + ": " + context.formattedValue
                        }
                    }
                },
                filter: (e: any) => {
                    return e.raw > 0;
                },
            },
        },
        scales: {
            x: {
                stacked: true,
            },
            y: {
                display: false,
                position: "left",
                stacked: true,
            },
            yB: {
                display: false,
                position: "right",
            }
        },
    }, theme.value));

    const chartData = computed<ChartDataConfig>(() => {
        const datasets = props.data.reduce<Record<string, Dataset>>((accumulator, value) => {
            Object.keys(value.executionCounts).forEach((state) => {
                if (accumulator[state] === undefined) {
                    accumulator[state] = {
                        label: state,
                        backgroundColor: scheme.value[state],
                        yAxisID: "y",
                        data: []
                    };
                }

                accumulator[state].data.push(value.executionCounts[state]);
            });

            return accumulator;
        }, {});

        const baseDatasets = props.big || props.global || props.duration
            ? [{
                type: "line",
                label: durationLabel,
                fill: "start",
                pointRadius: 0,
                borderWidth: 0.2,
                backgroundColor: Utils.hexToRgba(!darkTheme.value ? "#eaf0f9" : "#292e40", 0.5),
                borderColor: !darkTheme.value ? "#7081b9" : "#7989b4",
                yAxisID: "yB",
                data: props.data.map((value) => {
                    return value.duration.avg === 0 ? 0 : Utils.duration(value.duration.avg);
                })
            } as Dataset, ...Object.values(datasets)]
            : Object.values(datasets);

        return {
            labels: props.data.map(r => moment(r.startDate).format(getFormat(r.groupBy))),
            datasets: baseDatasets
        };
    });
</script>

