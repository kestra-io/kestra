<template>
    <div :id="uuid" :class="'executions-charts' + (this.global ? '' : ' mini')" v-if="dataReady">
        <el-tooltip
            popper-class="tooltip-stats"
            :placement="(global ? 'bottom' : 'left')"
        >
            <template #content>
                <span v-html="tooltipContent" />
            </template>
            <LineChart :ref="chartRef" :chart-data="chartData" :options="options" />
        </el-tooltip>
    </div>
</template>

<script>
    import {computed, defineComponent, ref} from "vue";
    import {LineChart} from "vue-chart-3";
    import Utils from "../../utils/utils.js";
    import {defaultConfig, tooltip} from "../../utils/charts.js";

    export default defineComponent({
        components: {LineChart},
        props: {
            data: {
                type: Array,
                required: true
            },
            global: {
                type: Boolean,
                default: () => false
            }
        },
        setup(props, {root}) {
            let duration = root.$i18n.t("duration")

            const chartRef = ref();
            const tooltipContent = ref("");
            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;
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
                                return Utils.humanDuration(context.raw);
                            }
                        }
                    }
                }
            }))

            const chartData = computed(() => {
                let avgData = props.data
                    .map((value) => {
                        return value.duration.avg === 0 ? null : Utils.duration(value.duration.avg);
                    });

                return {
                    labels: props.data.map(r => r.startDate),
                    datasets: [{
                        label: duration,
                        backgroundColor: !darkTheme ? "#eaf0f9" : "#292e40",
                        borderColor: !darkTheme ? "#7081b9" : "#7989b4",
                        data: avgData
                    }]
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
