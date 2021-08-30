<template>
    <div :id="uuid" :class="'executions-charts' + (this.global ? '' : ' mini')" v-if="dataReady">
        <LineChart :ref="chartRef" :chart-data="chartData" :options="options" />
        <b-tooltip
            custom-class="tooltip-stats"
            no-fade
            :target="uuid"
            :placement="(this.global ? 'bottom' : 'left')"
            triggers="hover"
        >
            <span v-html="tooltipContent" />
        </b-tooltip>
    </div>
</template>

<script>
    import {computed, defineComponent, ref} from "@vue/composition-api";
    import {LineChart} from "vue-chart-3";
    import Utils from "../../utils/utils.js";
    import {tooltip, defaultConfig} from "../../utils/charts.js";
    import humanizeDuration from "humanize-duration";

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
                                return humanizeDuration(context.raw * 1000);
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
                        backgroundColor: "#c7e7e5",
                        borderColor: "#1dbaaf",
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
