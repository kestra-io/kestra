<template>
    <div class="executions-charts big" v-if="dataReady">
        <el-tooltip
            placement="bottom"
            :persistent="false"
            :hide-after="0"
            transition=""
            :popper-class="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
        >
            <template #content>
                <span v-html="tooltipContent" />
            </template>
            <Bar ref="chartRef" :data="chartData" :options="options" />
        </el-tooltip>
    </div>
</template>

<script>
    import {computed, defineComponent, ref, getCurrentInstance} from "vue";
    import {Bar} from "vue-chartjs";
    import Utils from "../../utils/utils.js";
    import {
        defaultConfig,
        tooltip,
        getFormat,
    } from "../../utils/charts.js";
    import Logs from "../../utils/logs.js";

    export default defineComponent({
        components: {Bar},
        props: {
            data: {
                type: Array,
                required: true
            },
            namespace: {
                type: String,
                required: false,
                default: undefined
            },
            flowId: {
                type: String,
                required: false,
                default: undefined
            },
        },
        setup(props) {
            const moment = getCurrentInstance().appContext.config.globalProperties.$moment;
            const chartRef = ref();
            const tooltipContent = ref("");
            const dataReady = computed(() => props.data.length > 0)

            const options = computed(() => defaultConfig({
                plugins: {
                    tooltip: {
                        external: function (context) {
                            let content = tooltip(context.tooltip);
                            tooltipContent.value = content;
                        },
                        callbacks: {
                            label: function (context) {
                                if (context.formattedValue !== "0") {
                                    return context.dataset.label + ": " + context.formattedValue
                                }
                            }
                        },
                        filter: (e) => {
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
            }))

            const chartData = computed(() => {
                let datasets = props.data
                    .reduce(function (accumulator, value) {
                        Object.keys(value.counts).forEach(function (state) {
                            if (accumulator[state] === undefined) {
                                accumulator[state] = {
                                    label: state,
                                    backgroundColor: Logs.backgroundFromLevel(state),
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
                    labels: props.data.map(r => moment(r.timestamp).format(getFormat(r.groupBy))),
                    datasets: Object.values(datasets)
                }
            })

            return {chartData, tooltipContent, chartRef, options, dataReady};
        },
        data() {
            return {
                uuid: Utils.uid(),
            };
        },
    });
</script>

