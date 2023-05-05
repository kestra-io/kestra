<template>
    <div class="navbar-flow-metrics mb-1">
        <el-form-item class="m-1">
            <el-select
                :model-value="taskId"
                filterable
                :persistent="false"
                :placeholder="$t('task')"
                clearable
                @update:model-value="updateTask($event)"
            >
                <el-option
                    v-for="item in tasks"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>
        </el-form-item>
        <el-form-item class="m-1">
            <el-select
                :model-value="metric"
                filterable
                :persistent="false"
                :placeholder="$t('metric')"
                @update:model-value="updateMetric($event)"
            >
                <el-option
                    v-for="item in metrics"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>
        </el-form-item>
        <el-form-item class="m-1">
            <el-select
                :model-value="aggregation"
                filterable
                :persistent="false"
                :placeholder="$t('metric')"
                @update:model-value="updateQuery('aggregation',$event)"
            >
                <el-option
                    v-for="item in ['sum','avg','min','max']"
                    :key="item"
                    :label="$t(item)"
                    :value="item"
                >
                    {{ $t(item) }}
                </el-option>
            </el-select>
        </el-form-item>
        <el-form-item class="m-1">
            <date-range
                :start-date="$route.query.startDate"
                :end-date="$route.query.endDate"
                @update:model-value="onDateChange($event)"
            />
        </el-form-item>
    </div>
    <el-card>
        <BarChart ref="chartRef" :chart-data="chartData" :options="options" v-if="aggregatedMetric" />
    </el-card>
</template>

<script>
    import {BarChart} from "vue-chart-3";
    import {mapState} from "vuex";
    import moment from "moment";
    import DateRange from "../layout/DateRange.vue";

    export default {
        name: "FlowMetrics",
        components: {
            BarChart,
            DateRange,
        },
        created() {
            this.loadMetrics();
        },
        props: {
            preventRouteInfo: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            ...mapState("flow", ["flow", "metrics", "aggregatedMetric"]),
            chartData() {
                return {
                    labels: this.aggregatedMetric.aggregations.map(e => moment(e.date).format(this.getFormat(this.aggregatedMetric.groupBy))),
                    datasets: [
                        {
                            label: this.$t(this.aggregation) + " " + this.$t("of") + " " + this.metric,
                            backgroundColor: "#9F9DFF",
                            borderRadius: 4,
                            data: this.aggregatedMetric.aggregations.map(e => e.value ? e.value : 0)
                        }
                    ]
                };
            },
            options() {
                return {
                    scales: {
                        x: {
                            grid: {
                                borderColor: "#404559",
                                color: "#404559"
                            },
                            ticks: {
                                fontColor: "#918BA9",
                                autoSkip: true,
                                minRotation: 0,
                                maxRotation: 0
                            }
                        },
                        y: {
                            grid: {
                                borderColor: "#404559",
                                color: "#404559"
                            },
                            ticks: {
                                fontColor: "#918BA9"
                            }
                        }
                    }
                }
            },
            tasks() {
                return this.flow.tasks.map(e => e.id);
            }
        },
        data() {
            return {
                metric: null,
                aggregation: "sum",
                date: null,
                taskId: null,
            }
        },
        methods: {
            loadMetrics() {
                this.$store.dispatch(this.taskId ? "flow/loadTaskMetrics" : "flow/loadFlowMetrics", {
                    ...this.$route.params,
                    taskId: this.taskId
                })
                    .then(
                        () => {
                            if (this.metrics.length > 0) {
                                this.metric = this.metrics[0];
                                this.loadAggregatedMetrics();
                            }
                        }
                    );
            },
            loadAggregatedMetrics() {
                this.$store.dispatch(this.taskId ? "flow/loadTaskAggregatedMetrics" : "flow/loadFlowAggregatedMetrics", {
                    ...this.$route.params,
                    ...this.$route.query,
                    metric: this.metric,
                    aggregate: this.aggregation,
                    taskId: this.taskId
                });
            },
            onDateChange(keyOrObject) {
                let query = {...this.$route.query};
                for (const [key, value] of Object.entries(keyOrObject)) {
                    if (value === undefined || value === "" || value === null) {
                        delete query[key]
                    } else {
                        query[key] = value;
                    }
                }
                this.$router.push({query: query}).then(_ => {
                    this.loadAggregatedMetrics();
                })
            },
            updateQuery(key, value) {
                let query = {...this.$route.query};
                if (value === undefined || value === "" || value === null) {
                    delete query[key]
                } else {
                    query[key] = value;
                }
                this[key] = value;
                this.$router.push({query: query}).then(_ => {
                    this.loadAggregatedMetrics();
                })
            },
            updateMetric(metric) {
                this.metric = metric;
                this.loadAggregatedMetrics();
            },
            updateTask(task) {
                this.taskId = task;
                this.loadMetrics()
            },
            getFormat(groupBy) {
                switch (groupBy) {
                case "hour":
                    return "LLL";
                case "day":
                    return "l";
                case "week":
                    return "DD.MM";
                case "month":
                    return "MM.YYYY";
                }
            }
        }
    }
</script>

<style>
    .navbar-flow-metrics {
        display: flex;
        width: 100%;
    }
</style>