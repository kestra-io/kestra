<template>
    <nav>
        <collapse>
            <el-form-item>
                <el-select
                    :model-value="$route.query.task"
                    filterable
                    :persistent="false"
                    :placeholder="$t('task')"
                    clearable
                    @update:model-value="updateQuery({'task': $event})"
                >
                    <el-option
                        v-for="item in tasksWithMetrics"
                        :key="item"
                        :label="item"
                        :value="item"
                    >
                        {{ item }}
                    </el-option>
                </el-select>
            </el-form-item>
            <el-form-item>
                <el-select
                    :model-value="$route.query.metric"
                    filterable
                    :clearable="true"
                    :persistent="false"
                    :placeholder="$t('metric')"
                    @update:model-value="updateQuery({'metric': $event})"
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
            <el-form-item>
                <el-select
                    :model-value="$route.query.aggregation"
                    filterable
                    :clearable="true"
                    :persistent="false"
                    :placeholder="$t('aggregation')"
                    @update:model-value="updateQuery({'aggregation': $event})"
                >
                    <el-option
                        v-for="item in ['sum','avg','min','max']"
                        :key="item"
                        :label="$t(item)"
                        :value="item"
                    />
                </el-select>
            </el-form-item>
            <el-form-item>
                <date-filter
                    @update:is-relative="onDateFilterTypeChange"
                    @update:filter-value="updateQuery"
                />
            </el-form-item>
            <el-form-item>
                <refresh-button class="float-right" @refresh="load" :can-auto-refresh="canAutoRefresh" />
            </el-form-item>
        </collapse>
    </nav>

    <div v-bind="$attrs" v-loading="isLoading">
        <el-card>
            <el-tooltip
                effect="light"
                placement="bottom"
                :persistent="false"
                :hide-after="0"
                transition=""
                :popper-class="tooltipContent === '' ? 'd-none' : 'tooltip-stats'"
                v-if="aggregatedMetric"
            >
                <template #content>
                    <span v-html="tooltipContent" />
                </template>
                <Bar ref="chartRef" :data="chartData" :options="options" v-if="aggregatedMetric" />
            </el-tooltip>
            <span v-else>
                <el-alert type="info" :closable="false">
                    {{ $t("metric choice") }}
                </el-alert>
            </span>
        </el-card>
    </div>
</template>

<script>
    import {Bar} from "vue-chartjs";
    import {mapState} from "vuex";
    import moment from "moment";
    import {defaultConfig, getFormat, tooltip} from "../../utils/charts";
    import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";
    import Collapse from "../layout/Collapse.vue";
    import DateFilter from "../executions/date-select/DateFilter.vue";
    import RefreshButton from "../layout/RefreshButton.vue";

    export default {
        name: "FlowMetrics",
        components: {
            Collapse,
            Bar,
            DateFilter,
            RefreshButton
        },
        created() {
            this.loadMetrics();
        },
        computed: {
            ...mapState("flow", ["flow", "metrics", "aggregatedMetric","tasksWithMetrics"]),
            theme() {
                return localStorage.getItem("theme") || "light";
            },
            xGrid() {
                return this.theme === "light" ?
                    {}
                    : {
                        borderColor: "#404559",
                        color: "#404559"
                    }
            },
            yGrid() {
                return this.theme === "light" ?
                    {}
                    : {
                        borderColor: "#404559",
                        color: "#404559"
                    }
            },
            chartData() {
                return {
                    labels: this.aggregatedMetric.aggregations.map(e => moment(e.date).format(getFormat(this.aggregatedMetric.groupBy))),
                    datasets: [
                        !this.display ? [] : {
                            label: this.$t(this.$route.query.aggregation.toLowerCase()) + " " + this.$t("of") + " " + this.$route.query.metric,
                            backgroundColor: cssVariable("--el-color-success"),
                            borderRadius: 4,
                            data: this.aggregatedMetric.aggregations.map(e => e.value ? e.value : 0)
                        }
                    ]
                };
            },
            options() {
                const darken = this.theme === "light" ? cssVariable("--bs-gray-700") : cssVariable("--bs-gray-800");
                const lighten = this.theme === "light" ? cssVariable("--bs-gray-200") : cssVariable("--bs-gray-400");

                return defaultConfig({
                    plugins: {
                        tooltip: {
                            external: (context) => {
                                this.tooltipContent = tooltip(context.tooltip);
                            },
                        }
                    },
                    scales: {
                        x: {
                            display: true,
                            grid: {
                                borderColor: lighten,
                                color: lighten,
                                drawTicks: false
                            },
                            ticks: {
                                color: darken,
                                autoSkip: true,
                                minRotation: 0,
                                maxRotation: 0,
                            }
                        },
                        y: {
                            display: true,
                            grid: {
                                borderColor: lighten,
                                color: lighten,
                                drawTicks: false
                            },
                            ticks: {
                                color: darken
                            }
                        }
                    }
                })
            },
            display() {
                return this.$route.query.metric && this.$route.query.aggregation;
            },
            endDate() {
                if (this.$route.query.endDate) {
                    return this.$route.query.endDate;
                }
                return undefined;
            },
            startDate() {
                // This allow to force refresh this computed property especially when using timeRange
                this.refreshDates;
                if (this.$route.query.startDate) {
                    return this.$route.query.startDate;
                }
                if (this.$route.query.timeRange) {
                    return this.$moment().subtract(this.$moment.duration(this.$route.query.timeRange).as("milliseconds")).toISOString(true);
                }

                // the default is PT30D
                return this.$moment().subtract(30, "days").toISOString(true);
            }
        },
        data() {
            return {
                tooltipContent: undefined,
                isLoading: false,
                canAutoRefresh: false,
                refreshDates: false
            }
        },
        methods: {
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
            loadQuery(base) {
                return {
                    ...base,
                    startDate: this.startDate,
                    endDate: this.endDate
                }
            },
            loadMetrics() {
                this.$store.dispatch("flow/loadTasksWithMetrics",{...this.$route.params})
                this.$store
                    .dispatch(this.$route.query.task ? "flow/loadTaskMetrics" : "flow/loadFlowMetrics", this.loadQuery({
                        ...this.$route.params,
                        taskId: this.$route.query.task,
                    }))
                    .then(() => {
                        if (this.metrics.length > 0) {
                            if (this.$route.query.metric && !this.metrics.includes(this.$route.query.metric)) {
                                let query = {...this.$route.query};
                                delete query.metric;

                                this.$router.push({query: query}).then(_ => this.loadAggregatedMetrics());
                            } else {
                                this.loadAggregatedMetrics();
                            }
                        }
                    });
            },
            loadAggregatedMetrics() {
                this.isLoading = true;

                if (this.display) {
                    this.$store.dispatch(this.$route.query.task ? "flow/loadTaskAggregatedMetrics" : "flow/loadFlowAggregatedMetrics", this.loadQuery({
                        ...this.$route.params,
                        ...this.$route.query,
                        metric: this.$route.query.metric,
                        aggregate: this.$route.query.aggregation,
                        taskId: this.$route.query.task
                    }))
                } else {
                    this.$store.commit("flow/setAggregatedMetric", undefined)
                }
                this.isLoading = false;
            },
            updateQuery(queryParam) {
                let query = {...this.$route.query};
                for (const [key, value] of Object.entries(queryParam)) {
                    if (value === undefined || value === "" || value === null) {
                        delete query[key]
                    } else {
                        query[key] = value;
                    }
                }

                this.$router.push({query: query}).then(this.load);
            },
            load() {
                if (!this.$route.query.metric) {
                    this.loadMetrics();
                } else {
                    this.refreshDates = !this.refreshDates;
                    this.loadAggregatedMetrics();
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