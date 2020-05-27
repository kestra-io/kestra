<script>
import c3 from "c3";
import { debounce, cloneDeep, defaultsDeep, assign } from "lodash";

export default {
    name: "c3-chart",
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
            default: () => ({
                axis: {
                    x: { type: "category" }
                },
                tooltip: {
                    horizontal: false
                }
            })
        },
        data: {
            type: Object,
            required: true
        },
        type: {
            type: String,
            default: "bar"
        }
    },
    methods: {
        getArgs() {
            const size = {
                size: {
                    height: 50
                }
            };
            const data = this.getData();
            data.onclick = d => {
                const executionDay = this.$moment(
                    this.$chart.categories()[d.index]
                );
                const start = executionDay.unix() * 1000;
                executionDay.add(1, "d");
                const end = executionDay.unix() * 1000;

                const routeParams = {
                    name: "executionsByFlow",
                    params: {
                        namespace: data.row.item.namespace,
                        flowId: data.row.item.id
                    },
                    query: {
                        start,
                        end,
                        q: data.row.item.id,
                        status: d.id.toUpperCase()
                    }
                };
                this.$router.push(routeParams);
            };
            const config = this.getConfig();

            return defaultsDeep({ data }, size, config);
        },
        getData() {
            const { type } = this;
            const data = cloneDeep(this.data);

            // Fill data for empty days
            data.json = this.fillData(data.json);

            return defaultsDeep({ type }, data);
        },
        getConfig() {
            const config = cloneDeep(this.config);
            const color = {
                pattern: ["#bed863", "#d4664f", "#75bcdd", "#da9461"]
            };
            const axis = {
                y: {
                    show: false
                },
                x: {
                    show: false,
                    type: "category",
                    padding: {
                        left: 0,
                        right: 0
                    },
                    tick: {
                        multiline: true
                    }
                }
            };
            const legend = {
                show: false
            };
            const bar = {
                width: {
                    ratio: 0.5
                }
            };

            return defaultsDeep({ axis, color, legend, bar }, config);
        },
        update: debounce(function update() {
            // TODO : Use debounce
            const data = this.getData();
            this.$chart.load(data);
            this.$emit("update", data);
        }, 500),
        transform: debounce(function transform(...args) {
            this.$chart.transform(...args);
        }, 100),
        reload: debounce(function reload() {
            this.$emit("reloading");
            this.$chart.unload();
            this.$nextTick(() => {
                this.update();
            });
        }, 700),
        fillData(data) {
            const dateRange = this.$moment.range(this.startDate, this.endDate);

            let resultMetrics = [];

            for (let day of dateRange.by("days")) {
                let dateFormat = "YYYY-MM-DD";
                var d = day.format(dateFormat);

                let realMetric = data.filter(element => element.startDate == d);

                let emptyMetric = {
                    startDate: d,
                    success: 0,
                    failed: 0,
                    created: 0,
                    running: 0,
                    durationStats: null
                };

                if (realMetric.length > 0) {
                    let metrics = assign.apply(
                        null,
                        [emptyMetric].concat(realMetric)
                    );
                    resultMetrics.push(metrics);
                } else {
                    resultMetrics.push(emptyMetric);
                }
            }

            return resultMetrics;
        }
    },
    mounted() {
        const args = this.getArgs();

        this.$chart = c3.generate({
            bindto: this.$refs.root,
            ...args
        });

        this.$emit("init", args);
    },
    beforeDestroy() {
        this.$chart = this.$chart.destroy();
    }
};
</script>

<template>
    <div ref="root" class="chart-root"></div>
</template>
