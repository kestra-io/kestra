<template>
    <div :id="uuid" :class="'executions-charts' + (global ? (this.big ? ' big' : '') : ' mini')" v-if="dataReady">
        <BarChart ref="chartRef" :chart-data="chartData" :options="options" />
        <b-tooltip
            custom-class="tooltip-stats"
            no-fade
            :target="uuid"
            :placement="(global ? 'bottom' : 'left')"
            triggers="hover"
        >
            <span v-html="tooltipContent" />
        </b-tooltip>
    </div>
</template>

<script>
    import {computed, defineComponent, ref} from "@vue/composition-api"
    import {BarChart} from "vue-chart-3";
    import Utils from "../../utils/utils.js";
    import {defaultConfig, tooltip} from "../../utils/charts.js";
    import State from "../../utils/state";

    export default defineComponent({
        components: {BarChart},
        props: {
            data: {
                type: Array,
                required: true
            },
            global: {
                type: Boolean,
                default: () => false
            },
            big: {
                type: Boolean,
                default: () => false
            }
        },
        setup(props, {root}) {
            let duration = root.$i18n.t("duration")

            const chartRef = ref();
            const tooltipContent = ref("");

            const dataReady = computed(() => props.data.length > 0)

            const options = computed(() => defaultConfig({
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
                                if (context.dataset.yAxisID === "yAxesB" && context.raw !== 0) {
                                    return context.dataset.label + ": " + Utils.humanDuration(context.raw);
                                } else if (context.formattedValue !== "0") {
                                    return context.dataset.label + ": " + context.formattedValue
                                }
                            }
                        }
                    },
                },
                scales: {
                    xAxes: {
                        stacked: true,
                    },
                    yAxes: {
                        display: false,
                        position: "left",
                        stacked: true,
                    },
                    yAxesB: {
                        display: false,
                        position: "right",

                    }
                },
            }))

            const backgroundFromState = (state) => {
                return State.color()[state]
            }

            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("theme-dark") >= 0;

            const chartData = computed(() => {
                let datasets = props.data
                    .reduce(function (accumulator, value) {
                        Object.keys(value.executionCounts).forEach(function (state) {
                            if (accumulator[state] === undefined) {
                                accumulator[state] = {
                                    label: state,
                                    backgroundColor: backgroundFromState(state),
                                    borderRadius: 4,
                                    yAxisID: "yAxes",
                                    data: []
                                };
                            }

                            accumulator[state].data.push(value.executionCounts[state]);
                        });

                        return accumulator;
                    }, Object.create(null))

                return {
                    labels: props.data.map(r => r.startDate),
                    datasets: props.big || props.global ?
                        [{
                            order: 2,
                            type: "line",
                            label: duration,
                            fill: "start",
                            pointRadius: 0,
                            opacity: 0.5,
                            borderWidth: 0.2,
                            backgroundColor: !darkTheme ? "#eaf0f9" : "#292e40",
                            borderColor: !darkTheme ? "#7081b9" : "#7989b4",
                            yAxisID: "yAxesB",
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