<template>
    <div :id="uuid" :class="'executions-charts' + (this.global ? '' : ' mini')" v-if="dataReady">
        <current-chart :data="collections" :options="options" />
        <b-tooltip
            custom-class="tooltip-stats"
            no-fade
            :target="uuid"
            :placement="(this.global ? 'bottom' : 'left')"
            triggers="hover"
        >
            <span v-html="tooltip" />
        </b-tooltip>
    </div>
</template>

<script>
    import {Line} from "vue-chartjs"
    import humanizeDuration from "humanize-duration";
    import {tooltip, defaultConfig} from "../../utils/charts.js";
    import Utils from "../../utils/utils";

    const CurrentChart = {
        extends: Line,
        props: {
            data: {
                type: Object,
                required: true
            },
            options: {
                type: Object,
                required: true
            }
        },
        mounted() {
            setTimeout(() => {
                this.renderChart(this.data, this.options);
            }, 0)
        },
    };

    export default {
        components: {
            CurrentChart
        },
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
        data() {
            return {
                uuid: Utils.uid(),
                tooltip: undefined
            };
        },
        computed: {
            dataReady() {
                return this.data.length > 0;
            },
            collections() {
                let avgData = this.data
                    .map((value) => {
                        return value.duration.avg === 0 ? null : value.duration.avg;
                    });

                return {
                    labels: this.data.map(r => r.startDate),
                    datasets: [{
                        label: "Duration",
                        backgroundColor: "#c7e7e5",
                        fill: "start",
                        pointRadius: 1,
                        borderWidth: 1,
                        borderColor: "#1dbaaf",
                        data: avgData
                    }]
                }
            },

            options() {
                let self = this

                return defaultConfig({
                    tooltips: {
                        custom: function(tooltipModel) {
                            let content = tooltip(tooltipModel);
                            if (content) {
                                self.tooltip = content;
                            }
                        },
                        callbacks: {
                            label: function(tooltipItem) {
                                return humanizeDuration(tooltipItem.yLabel * 1000);
                            }
                        }
                    },

                })
            }
        }
    }
</script>
