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
    import {computed, ref} from "vue";
    import {Bar} from "vue-chartjs";
    import moment from "moment";
    import {useMiscStore} from "override/stores/misc";
    import {
        defaultConfig,
        tooltip,
        getFormat,
    } from "../dashboard/composables/charts";
    import * as Logs from "../../utils/logs";

    interface LogData {
        timestamp: string;
        groupBy: string;
        counts: Record<string, number>;
    }

    const props = withDefaults(defineProps<{
        data: LogData[];
        namespace?: string;
        flowId?: string;
    }>(), {
        namespace: undefined,
        flowId: undefined
    });

    const chartRef = ref();
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

    const chartData = computed(() => {
        let datasets = props.data
            .reduce(function (accumulator: Record<string, any>, value: LogData) {
                Object.keys(value.counts).forEach(function (state: string) {
                    if (accumulator[state] === undefined) {
                        accumulator[state] = {
                            label: state,
                            backgroundColor: Logs.chartColorFromLevel(state),
                            borderRadius: 4,
                            yAxisID: "y",
                            data: []
                        };
                    }

                    accumulator[state].data.push(value.counts[state]);
                });

                return accumulator;
            }, Object.create(null))

        datasets = Logs.sort(datasets);

        return {
            labels: props.data.map((r: LogData) => moment(r.timestamp).format(getFormat(r.groupBy))),
            datasets: Object.values(datasets)
        }
    });
</script>

