<template>
    <div :class="'executions-charts' + (global ? (this.big ? ' big' : '') : ' mini')" v-if="dataReady">
        <el-tooltip
            popper-class="tooltip-stats"
            :placement="(global ? 'bottom' : 'left')"
        >
            <template #content>
                <span v-html="tooltipContent" />
            </template>
            <BarChart ref="chartRef" :chart-data="chartData" :options="options" />
        </el-tooltip>
    </div>
</template>

<script>
    import {computed, defineComponent, ref} from "vue"
    import {BarChart} from "vue-chart-3";
    import Utils from "../../utils/utils.js";
    import {defaultConfig, tooltip, chartClick} from "../../utils/charts.js";
    import State from "../../utils/state";
    import {useI18n} from "vue-i18n";

    export default defineComponent({
        components: {BarChart},
        props: {
            data: {
                type: Array,
                required: true
            },
            duration: {
                type: Boolean,
                default: () => false
            },
            global: {
                type: Boolean,
                default: () => false
            },
            big: {
                type: Boolean,
                default: () => false
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
        setup(props, {root}) {
            const {t} = useI18n({useScope: "global"});

            let duration = t("duration")

            const chartRef = ref();
            const tooltipContent = ref("");

            const dataReady = computed(() => props.data.length > 0)

            const options = computed(() => defaultConfig({
                onClick: (e, elements) => {
                    if (elements.length > 0 && elements[0].index !== undefined && elements[0].datasetIndex !== undefined ) {
                        chartClick(
                            root,
                            {
                                date: e.chart.data.labels[elements[0].index],
                                status: e.chart.data.datasets[elements[0].datasetIndex].label,
                                namespace: props.namespace,
                                flowId: props.flowId
                            }
                        )
                    }
                },
                plugins: {
                    tooltip: {
                        external: function (context) {
                            let content = tooltip(context.tooltip);
                            if (content) {
                                tooltipContent.value = content;
                            }
                        },
                        callbacks: {
                            label: function(context) {
                                if (context.dataset.yAxisID === "yB" && context.raw !== 0) {
                                    return context.dataset.label + ": " + Utils.humanDuration(context.raw);
                                } else if (context.formattedValue !== "0") {
                                    return context.dataset.label + ": " + context.formattedValue
                                }
                            }
                        }
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

            const backgroundFromState = (state) => {
                return State.color()[state]
            }

            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

            const chartData = computed(() => {
                let datasets = props.data
                    .reduce(function (accumulator, value) {
                        Object.keys(value.executionCounts).forEach(function (state) {
                            if (accumulator[state] === undefined) {
                                accumulator[state] = {
                                    label: state,
                                    backgroundColor: backgroundFromState(state),
                                    borderRadius: 4,
                                    yAxisID: "y",
                                    data: []
                                };
                            }

                            accumulator[state].data.push(value.executionCounts[state]);
                        });

                        return accumulator;
                    }, Object.create(null))

                return {
                    labels: props.data.map(r => r.startDate),
                    datasets: props.big || props.global || props.duration ?
                        [{
                            type: "line",
                            label: duration,
                            fill: "start",
                            pointRadius: 0,
                            borderWidth: 0.2,
                            backgroundColor: Utils.hexToRgba(!darkTheme ? "#eaf0f9" : "#292e40", 0.5),
                            borderColor: !darkTheme ? "#7081b9" : "#7989b4",
                            yAxisID: "yB",
                            data: props.data
                                .map((value) => {
                                    return value.duration.avg === 0 ? 0 : Utils.duration(value.duration.avg);
                                })
                        }, ...Object.values(datasets)] :
                        Object.values(datasets)
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