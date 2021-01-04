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
    import {Bar} from "vue-chartjs"
    import Utils from "../../utils/utils.js";
    import {tooltip, defaultConfig} from "../../utils/charts.js";
    import State from "../..//utils/state";

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
            },
            big: {
                type: Boolean,
                default: () => true
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
                                    data: []
                                };
                            }

                            accumulator[state].data.push(value.executionCounts[state]);
                        });

                        return accumulator;
                    }, Object.create(null))


                return {
                    labels: this.data.map(r => r.startDate),
                    datasets: Object.values(datasets)
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
                        }
                    },

                    scales: {
                        xAxes: [{
                            stacked: true,
                        }],
                        yAxes: [{
                            stacked: true,
                        }]
                    },
                })
            }
        }
    }
</script>
