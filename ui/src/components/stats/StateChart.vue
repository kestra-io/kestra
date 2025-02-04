<template>
    <div :class="'executions-charts' + (global ? (big ? ' big' : '') : ' mini')" v-if="dataReady">
        <el-tooltip
            effect="light"
            :placement="(global ? 'bottom' : 'left')"
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
    import {useRoute, useRouter} from "vue-router"
    import {Bar} from "vue-chartjs";
    import Utils from "../../utils/utils.js";
    import {getScheme} from "../../utils/scheme.js";
    import {defaultConfig, tooltip, chartClick, getFormat} from "../../utils/charts.js";
    import {useI18n} from "vue-i18n";

    export default defineComponent({
        components: {Bar},
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
        setup(props) {
            const moment = getCurrentInstance().appContext.config.globalProperties.$moment;
            const route = useRoute();
            const router = useRouter();
            const {t} = useI18n({useScope: "global"});

            let duration = t("duration")

            const chartRef = ref();
            const tooltipContent = ref("");

            const dataReady = computed(() => props.data.length > 0)

            const options = computed(() => defaultConfig({
                barThickness: 4,
                onClick: (e, elements) => {
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
                        external: function (context) {
                            let content = tooltip(context.tooltip);
                            tooltipContent.value = content;
                        },
                        callbacks: {
                            label: function (context) {
                                if (context.dataset.yAxisID === "yB" && context.raw !== 0) {
                                    return context.dataset.label + ": " + Utils.humanDuration(context.raw);
                                } else if (context.formattedValue !== "0") {
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

            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

            const chartData = computed(() => {
                let datasets = props.data
                    .reduce(function (accumulator, value) {
                        Object.keys(value.executionCounts).forEach(function (state) {
                            if (accumulator[state] === undefined) {
                                accumulator[state] = {
                                    label: state,
                                    backgroundColor: getScheme(state),
                                    yAxisID: "y",
                                    data: []
                                };
                            }

                            accumulator[state].data.push(value.executionCounts[state]);
                        });

                        return accumulator;
                    }, Object.create(null))

                return {
                    labels: props.data.map(r => moment(r.startDate).format(getFormat(r.groupBy))),
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

