<template>
    <div :id="uuid" :class="'executions-charts' + (this.global ? (this.big ? ' big' : '') : ' mini')" v-if="dataReady">
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
    import {Bar} from "vue-chartjs"
    import Utils from "../../utils/utils.js";
    import {tooltip, defaultConfig} from "../../utils/charts.js";
    import State from "../..//utils/state";
    import humanizeDuration from "humanize-duration";

    const CurrentChart = {
        extends: Bar,
        props: {
            data: {
                type: Object,
                required: true
            },
            options: {
                type: Object,
                required: true
            },
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
            },
            big: {
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
        methods: {
            backgroundFromState(state) {
                return State.color()[state]
            }
        },
        computed: {
            dataReady() {
                return this.data.length > 0;
            },
            collections() {
                let self = this;

                let datasets = this.data
                    .reduce(function (accumulator, value) {
                        Object.keys(value.executionCounts).forEach(function (state) {
                            if (accumulator[state] === undefined) {
                                accumulator[state] = {
                                    label: state,
                                    backgroundColor: self.backgroundFromState(state),
                                    yAxisID: "A",
                                    data: []
                                };
                            }

                            accumulator[state].data.push(value.executionCounts[state]);
                        });

                        return accumulator;
                    }, Object.create(null))

                return {
                    labels: this.data.map(r => r.startDate),
                    datasets: !this.big ? Object.values(datasets) : [{
                        type: "line",
                        label: this.$t("duration"),
                        backgroundColor: "#c7e7e5",
                        fill: "start",
                        pointRadius: 1,
                        borderWidth: 1,
                        borderColor: "#1dbaaf",
                        yAxisID: "B",
                        data: this.data
                            .map((value) => {
                                return value.duration.avg === 0 ? 0 : Utils.duration(value.duration.avg);
                            })
                    }, ...Object.values(datasets), ]
                }
            },
            options() {
                let self = this

                return defaultConfig({
                    tooltips: {
                        custom: function (tooltipModel) {
                            let content = tooltip(tooltipModel);
                            if (content) {
                                self.tooltip = content;
                            }
                        },
                        callbacks: {
                            label: function(tooltipItem, data) {
                                const dataset = data.datasets[tooltipItem.datasetIndex];
                                if (dataset.yAxisID === "B") {
                                    return dataset.label + ": " + humanizeDuration(tooltipItem.yLabel * 1000);
                                } else {
                                    return dataset.label + ": " + tooltipItem.value
                                }
                            }
                        }
                    },
                    scales: {
                        xAxes: [{
                            stacked: true,
                        }],
                        yAxes: [
                            {
                                id: "A",
                                position: "left",
                                stacked: true,
                            },
                            {
                                id: "B",
                                display: false,
                                position: "right",
                            }
                        ]
                    },
                })
            }
        }
    }
</script>
