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
    import {defineComponent, computed, ref} from "@vue/composition-api"
    import {BarChart} from "vue-chart-3";
    import Utils from "../../utils/utils.js";
    import {tooltip, defaultConfig} from "../../utils/charts.js";
    import State from "../..//utils/state";
    import humanizeDuration from "humanize-duration";

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
                                if (context.dataset.yAxisID === "yAxesB") {
                                    return context.dataset.label + ": " + humanizeDuration(context.raw * 1000);
                                } else {
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

            const chartData = computed(() => {
                let datasets = props.data
                    .reduce(function (accumulator, value) {
                        Object.keys(value.executionCounts).forEach(function (state) {
                            if (accumulator[state] === undefined) {
                                accumulator[state] = {
                                    label: state,
                                    backgroundColor: backgroundFromState(state),
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
                    datasets: !props.big ? Object.values(datasets) : [{
                        order: 2,
                        type: "line",
                        label: duration,
                        backgroundColor: "#c7e7e5",
                        fill: "start",
                        pointRadius: 1,
                        borderWidth: 1,
                        borderColor: "#1dbaaf",
                        yAxisID: "yAxesB",
                        data: props.data
                            .map((value) => {
                                return value.duration.avg === 0 ? 0 : Utils.duration(value.duration.avg);
                            })
                    }, ...Object.values(datasets), ]
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