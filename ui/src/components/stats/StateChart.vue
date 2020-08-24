<template>
    <div class="state-charts">
        <vue-c3 :handler="handler"></vue-c3>
    </div>
</template>

<script>
    import Vue from 'vue'
    import VueC3 from 'vue-c3'
    import {defaultsDeep} from "lodash";
    import {dateFill, tooltipPosition} from "./StatsUtils";

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
            namespace: {
                type: String,
            },
            flowId: {
                type: String,
                required: true
            }
        },

        methods: {
            getArgs() {
                const data = this.getData();

                if (this.namespace && this.flowId) {
                    const self = this;
                    data.onclick = function (d) {

                        const executionDay = self.$moment(
                            this.categories()[d.index]
                        );
                        const start = executionDay.unix() * 1000;
                        executionDay.add(1, "d");
                        const end = executionDay.unix() * 1000;

                        const routeParams = {
                            name: "flowEdit",
                            params: {
                                namespace: self.namespace,
                                id: self.flowId,
                            },
                            query: {
                                tab: "executions",
                                start,
                                end,
                                status: d.id.toUpperCase()
                            }
                        };

                        self.$router.push(routeParams);
                    };
                }

                const config = this.getConfig();

                return defaultsDeep({data: data}, config);
            },
            getData: function () {
                return {
                    json: this.fillData(this.data
                        .map(d => {
                            const r = {
                                startDate: d.startDate,
                            };

                            d.executionCounts
                                .forEach(c => {
                                    return r[c.state.toLowerCase()] = c.count;
                                })

                            return r;
                        })
                    )
                };
            },
            getConfig() {
                const statuses = ["success", "failed", "created", "running"];

                const defaultConfig = {
                    data: {
                        type: 'bar',
                        keys: {x: "startDate", value: statuses},
                        groups: [statuses],
                    },
                    size: {
                        height: 50
                    },
                    axis: {
                        y: {
                            show: false,
                            padding: 0,
                            min: 0,
                        },
                        x: {
                            show: true,
                            type: "category",
                            padding: 0,
                            height: 1
                        }
                    },
                    color: {
                        pattern: ["#43ac6a", "#F04124", "#75bcdd", "#1AA5DE"]
                    },
                    legend: {
                        show: false
                    },
                    tooltip: {
                        position: tooltipPosition
                    },
                    bar: {
                        width: {
                            ratio: 0.7
                        }
                    }
                }

                return defaultsDeep(defaultConfig, this.config);
            },
            fillData(data) {
                return dateFill(data, this.startDate, this.endDate, "startDate", this.dateFormat, {
                    success: 0,
                    failed: 0,
                    created: 0,
                    running: 0,
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
.state-charts {
    .c3-axis-x path, .c3-axis-x line {
        stroke: transparent;
    }
}
</style>


