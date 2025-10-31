<template>
    <div class="executions-charts big" v-if="dataReady">
        <el-tooltip
            effect="light"
            placement="bottom"
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
    import {computed, ref, getCurrentInstance, ComputedRef} from "vue";
    import {Bar} from "vue-chartjs";
    import {useMiscStore} from "override/stores/misc";
    import {
        defaultConfig,
        tooltip,
        getFormat,
    } from "../dashboard/composables/charts";
    import Logs from "../../utils/logs";
    import type {ChartData} from "chart.js";

    // Import LogLevel type from logs utility
    type LogLevel = "ERROR" | "WARN" | "INFO" | "DEBUG" | "TRACE";

    interface ChartEntry {
        timestamp: string;
        groupBy: string;
        counts: Partial<Record<LogLevel, number>>;
    }

    interface DataSet {
        label: string;
        backgroundColor: string | null;
        borderRadius: number;
        yAxisID: string;
        data: number[];
    }

    const props = withDefaults(defineProps<{
        data: ChartEntry[];
        namespace?: string;
        flowId?: string;
    }>(), {
        namespace: undefined,
        flowId: undefined
    });

    const app = getCurrentInstance();
    const moment = app?.appContext.config.globalProperties.$moment;
    if (!moment) {
        throw new Error("moment is not defined in the Vue app instance");
    }
    
    const chartRef = ref<InstanceType<typeof Bar> | null>(null);
    const tooltipContent = ref("");
    
    const miscStore = useMiscStore();

    const dataReady = computed(() => props.data?.length > 0);

    const options = computed(() => defaultConfig({
        plugins: {
            tooltip: {
                external: function (context: any) {
                    let content = tooltip(context.tooltip);
                    tooltipContent.value = content ?? "";
                },
                callbacks: {
                    label: function (context: any) {
                        if (context.formattedValue !== "0") {
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
    }, miscStore.theme) as any);

    const chartData: ComputedRef<ChartData<"bar">> = computed(() => {
        // Create a type-safe accumulator for datasets
        const datasets = props.data.reduce((accumulator: Record<LogLevel, DataSet>, value: ChartEntry) => {
            (Object.keys(value.counts) as LogLevel[]).forEach((state) => {
                if (!accumulator[state]) {
                    const backgroundColor = Logs.chartColorFromLevel(state);
                    accumulator[state] = {
                        label: state,
                        backgroundColor: backgroundColor,
                        borderRadius: 4,
                        yAxisID: "y",
                        data: []
                    };
                }
                
                const count = value.counts[state];
                if (typeof count === "number") {
                    accumulator[state].data.push(count);
                } else {
                    accumulator[state].data.push(0); // Default to 0 if count is undefined
                }
            });

            return accumulator;
        }, {} as Record<LogLevel, DataSet>);

        const sortedDatasets = Logs.sort(datasets);

        return {
            labels: props.data.map((r: ChartEntry) => moment(r.timestamp).format(getFormat(r.groupBy))),
            datasets: Object.values(sortedDatasets)
        };
    });


</script>