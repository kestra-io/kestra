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
    import {useRoute, useRouter} from "vue-router";
    import {useI18n} from "vue-i18n";
    import {Bar} from "vue-chartjs";
    import moment from "moment";
    import {useScheme} from "../../utils/scheme";
    import Utils, {useTheme} from "../../utils/utils";
    import {defaultConfig, tooltip, chartClick, getFormat} from "../dashboard/composables/charts";

    interface ExecutionData {
        startDate: string;
        groupBy: string;
        executionCounts: Record<string, number>;
        duration: {
            avg: number;
        };
    }

    const props = withDefaults(defineProps<{
        data: ExecutionData[];
        duration?: boolean;
        global?: boolean;
        big?: boolean;
        namespace?: string;
        flowId?: string;
    }>(), {
        duration: false,
        global: false,
        big: false,
        namespace: undefined,
        flowId: undefined
    });

    const route = useRoute();
    const router = useRouter();
    const {t} = useI18n({useScope: "global"});

    const chartRef = ref();
    const tooltipContent = ref("");

    const theme = useTheme();
    const scheme = useScheme();
    
    const dataReady = computed(() => props.data?.length > 0);

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
                    },
                    undefined,
                    undefined
                )
            }
        },
        plugins: {
            tooltip: {
                external: function (context: any) {
                    let content = tooltip(context.tooltip);
                    tooltipContent.value = content ?? "";
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
    }, theme.value) as any);

    const darkTheme = computed(() => theme.value === "dark");

    const chartData = computed(() => {
        let datasets = props.data
            .reduce(function (accumulator: Record<string, any>, value: ExecutionData) {
                Object.keys(value.executionCounts).forEach(function (state: string) {
                    if (accumulator[state] === undefined) {
                        accumulator[state] = {
                            label: state,
                            backgroundColor: (scheme.value as any)[state],
                            yAxisID: "y",
                            data: []
                        };
                    }

                    accumulator[state].data.push(value.executionCounts[state]);
                });

                return accumulator;
            }, Object.create(null))

        return {
            labels: props.data.map((r: ExecutionData) => moment(r.startDate).format(getFormat(r.groupBy))),
            datasets: props.big || props.global || props.duration ?
                [{
                    type: "line",
                    label: t("duration"),
                    fill: "start",
                    pointRadius: 0,
                    borderWidth: 0.2,
                    backgroundColor: Utils.hexToRgba(!darkTheme.value ? "#eaf0f9" : "#292e40", 0.5),
                    borderColor: !darkTheme.value ? "#7081b9" : "#7989b4",
                    yAxisID: "yB",
                    data: props.data
                        .map((value: ExecutionData) => {
                            return value.duration.avg === 0 ? 0 : Utils.duration(String(value.duration.avg));
                        })
                }, ...Object.values(datasets)] :
                Object.values(datasets)
        }
    });

</script>

