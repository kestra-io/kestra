<template>
    <KestraFilter
        prefix="flow_metrics"
        :include="[
            'task',
            'metric',
            'aggregation',
            'relative_date',
            'absolute_date',
        ]"
        :values="{
            task: tasksWithMetrics.map((value) => ({
                label: value,
                value,
            })),
            metric: metrics.map((value) => ({
                label: value,
                value,
            })),
        }"
        :buttons="{
            refresh: {shown: true, callback: load},
            settings: {shown: false}
        }"
    />

    <div v-bind="$attrs" v-loading="isLoading">
        <el-card>
            <el-tooltip
                effect="light"
                placement="bottom"
                :persistent="false"
                :hide-after="0"
                transition=""
                :popper-class="
                    tooltipContent === '' ? 'd-none' : 'tooltip-stats'
                "
                v-if="aggregatedMetric"
            >
                <template #content>
                    <span v-html="tooltipContent" />
                </template>
                <Bar
                    ref="chartRef"
                    :data="chartData"
                    :options="options"
                    v-if="aggregatedMetric"
                />
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
    import {cssVariable} from "@kestra-io/ui-libs";
    import KestraFilter from "../filter/KestraFilter.vue";

    export default {
        name: "FlowMetrics",
        components: {
            Bar,
            KestraFilter,
        },
        created() {
            this.loadMetrics();
        },
        computed: {
            ...mapState("flow", [
                "flow",
                "metrics",
                "aggregatedMetric",
                "tasksWithMetrics",
            ]),
            theme() {
                return localStorage.getItem("theme") || "light";
            },
            xGrid() {
                return this.theme === "light"
                    ? {}
                    : {
                        borderColor: "#404559",
                        color: "#404559",
                    };
            },
            yGrid() {
                return this.theme === "light"
                    ? {}
                    : {
                        borderColor: "#404559",
                        color: "#404559",
                    };
            },
            chartData() {
                return {
                    labels: this.aggregatedMetric.aggregations.map((e) =>
                        moment(e.date).format(
                            getFormat(this.aggregatedMetric.groupBy),
                        ),
                    ),
                    datasets: [
                        !this.display
                            ? []
                            : {
                                label:
                                    this.$t(this.$route.query.aggregation) +
                                    " " +
                                    this.$t("of") +
                                    " " +
                                    this.$route.query.metric,
                                backgroundColor:
                                    cssVariable("--el-color-success"),
                                borderRadius: 4,
                                data: this.aggregatedMetric.aggregations.map(
                                    (e) => (e.value ? e.value : 0),
                                ),
                            },
                    ],
                };
            },
            options() {
                const darken =
                    this.theme === "light"
                        ? cssVariable("--bs-gray-700")
                        : cssVariable("--bs-gray-800");
                const lighten =
                    this.theme === "light"
                        ? cssVariable("--bs-gray-200")
                        : cssVariable("--bs-gray-400");

                return defaultConfig({
                    plugins: {
                        tooltip: {
                            external: (context) => {
                                this.tooltipContent = tooltip(context.tooltip);
                            },
                        },
                    },
                    scales: {
                        x: {
                            display: true,
                            grid: {
                                borderColor: lighten,
                                color: lighten,
                                drawTicks: false,
                            },
                            ticks: {
                                color: darken,
                                autoSkip: true,
                                minRotation: 0,
                                maxRotation: 0,
                            },
                        },
                        y: {
                            display: true,
                            grid: {
                                borderColor: lighten,
                                color: lighten,
                                drawTicks: false,
                            },
                            ticks: {
                                color: darken,
                            },
                        },
                    },
                });
            },
            display() {
                return this.$route.query.metric && this.$route.query.aggregation;
            },
        },
        data() {
            return {
                tooltipContent: undefined,
                isLoading: false,
            };
        },
        methods: {
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
            loadQuery(base) {
                return {
                    ...base,
                    startDate: this.startDate,
                    endDate: this.endDate,
                };
            },
            loadMetrics() {
                this.$store.dispatch("flow/loadTasksWithMetrics", {
                    ...this.$route.params,
                });
                this.$store
                    .dispatch(
                        this.$route.query.task
                            ? "flow/loadTaskMetrics"
                            : "flow/loadFlowMetrics",
                        this.loadQuery({
                            ...this.$route.params,
                            taskId: this.$route.query.task,
                        }),
                    )
                    .then(() => {
                        if (this.metrics.length > 0) {
                            if (
                                this.$route.query.metric &&
                                !this.metrics.includes(this.$route.query.metric)
                            ) {
                                let query = {...this.$route.query};
                                delete query.metric;

                                this.$router
                                    .push({query: query})
                                    .then((_) => this.loadAggregatedMetrics());
                            } else {
                                this.loadAggregatedMetrics();
                            }
                        }
                    });
            },
            loadAggregatedMetrics() {
                this.isLoading = true;

                if (this.display) {
                    this.$store.dispatch(
                        this.$route.query.task
                            ? "flow/loadTaskAggregatedMetrics"
                            : "flow/loadFlowAggregatedMetrics",
                        this.loadQuery({
                            ...this.$route.params,
                            ...this.$route.query,
                            metric: this.$route.query.metric,
                            aggregate: this.$route.query.aggregation,
                            taskId: this.$route.query.task,
                        }),
                    );
                } else {
                    this.$store.commit("flow/setAggregatedMetric", undefined);
                }
                this.isLoading = false;
            },
            updateQuery(queryParam) {
                let query = {...this.$route.query};
                for (const [key, value] of Object.entries(queryParam)) {
                    if (value === undefined || value === "" || value === null) {
                        delete query[key];
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
                    this.loadAggregatedMetrics();
                }
            },
        },
        watch: {
            "$route.query": {
                handler(query) {
                    if (!query.metric) {
                        this.loadMetrics();
                    } else {
                        this.loadAggregatedMetrics();
                    }
                },
            },
        },
    };
</script>

<style>
.navbar-flow-metrics {
    display: flex;
    width: 100%;
}
</style>
