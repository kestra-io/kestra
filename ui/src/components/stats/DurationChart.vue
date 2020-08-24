<template>
    <div class="duration-charts">
        <vue-c3 :handler="handler"></vue-c3>
    </div>
</template>

<script>
    import Vue from 'vue'
    import VueC3 from 'vue-c3'
    import {defaultsDeep} from "lodash";
    import {dateFill, tooltipPosition} from "./StatsUtils";
    import humanizeDuration from "humanize-duration";

    export default {
        name: "c3-chart",
        components: {
            VueC3
        },
        data() {
            return {
                handler: new Vue(),
            }
        },
        props: {
            startDate: {
                type: Date,
                required: true
            },
            endDate: {
                type: Date,
                required: true
            },
            dateFormat: {
                type: String,
                required: true
            },
            config: {
                type: Object,
                default: () => ({})
            },
            data: {
                type: Array,
                required: true
            },
        },

        methods: {
            getArgs() {
                const data = this.getData();
                const config = this.getConfig();

                return defaultsDeep({data: data}, config);
            },
            getData: function () {
                return {
                    json: this.fillData(this.data
                        .map(d => {
                            return {
                                startDate: d.startDate,
                                duration: d.duration ? d.duration.avg : null
                            };
                        })
                    )
                };
            },
            getConfig() {
                const defaultConfig = {
                    data: {
                        type: 'area',
                        keys: {x: "startDate", value: ["duration"]},
                    },
                    size: {
                        height: 50
                    },
                    axis: {
                        y: {
                            show: false,
                            min: 0,
                            padding: 0,
                            tick: {
                                format: function (d) { return humanizeDuration(d * 1000); }
                            }
                        },
                        x: {
                            show: true,
                            type: "category",
                            padding: 0,
                            height: 1
                        }
                    },
                    point: {
                        r: 1
                    },
                    color: {
                        pattern: ["#1DBAAF"]
                    },
                    legend: {
                        show: false
                    },
                    tooltip: {
                        position: tooltipPosition
                    }
                }

                return defaultsDeep(defaultConfig, this.config);
            },
            fillData(data) {
                return dateFill(data, this.startDate, this.endDate, "startDate", this.dateFormat, {
                    duration: null
                });
            }
        },
        mounted() {
            const args = this.getArgs();
            this.handler.$emit('init', args)
        }
    };
</script>

<style lang="scss">
.duration-charts {
    .c3-axis-x path, .c3-axis-x line {
        stroke: transparent;
    }
}
</style>
