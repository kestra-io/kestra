<template>
    <KSFilter
        :configuration="flowMetricFilter"
        :prefix="'flow-metrics'"
        :tableOptions="{
            chart: {shown: false},
            columns: {shown: false},
            refresh: {shown: true, callback: load}
        }"
        legacyQuery
        :defaultScope="false"
        :defaultTimeRange="false"
    />

    <div v-bind="$attrs" v-loading="isLoading">
        <el-card>
            <el-tooltip
                effect="light"
                placement="bottom"
                :persistent="false"
                :hideAfter="0"
                transition=""
                :popperClass="
                    tooltipContent === '' ? 'd-none' : 'tooltip-stats'
                "
                v-if="flowStore.aggregatedMetrics"
            >
                <template #content>
                    <span v-html="tooltipContent" />
                </template>
                <Bar
                    ref="chartRef"
                    :data="chartData"
                    :options="options"
                    v-if="flowStore.aggregatedMetrics"
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

<script setup lang="ts">
    import {useFlowMetricFilter} from "../filter/configurations";

    const flowMetricFilter = useFlowMetricFilter();
</script>

<script lang="ts">
    import {defineComponent} from "vue";
    import {Bar} from "vue-chartjs";
    import {mapStores} from "pinia";
    import {useMiscStore} from "override/stores/misc";
    import {useFlowStore} from "../../stores/flow";
    import moment from "moment";
    import {defaultConfig, getFormat, tooltip} from "../dashboard/composables/charts";
    import {cssVariable} from "@kestra-io/ui-libs";
    import KSFilter from "../filter/components/KSFilter.vue";

    export default defineComponent({
        name: "FlowMetrics",
        components: {
            Bar,
            KSFilter,
        },
        created() {
            this.loadMetrics();
        },
        computed: {
            ...mapStores(useMiscStore, useFlowStore),
            xGrid() {
                return this.miscStore.theme === "light"
                    ? {}
                    : {
                        borderColor: "#404559",
                        color: "#404559",
                    };
            },
            yGrid() {
                return this.miscStore.theme === "light"
                    ? {}
                    : {
                        borderColor: "#404559",
                        color: "#404559",
                    };
            },
            chartData() {
                return {
                    labels: this.flowStore.aggregatedMetrics.aggregations.map((e) =>
                        moment(e.date).format(
                            getFormat(this.flowStore.aggregatedMetrics.groupBy),
                        ),
                    ),
                    datasets: [
                        !this.display
                            ? []
                            : {
                                label: `${this.$t([this.$route.query.aggregation].flat()[0]?.toLowerCase())} ${this.$t("of")} ${this.$route.query.metric}`,
                                backgroundColor:
                                    cssVariable("--el-color-success"),
                                borderRadius: 4,
                                data: this.flowStore.aggregatedMetrics.aggregations.map(
                                    (e) => (e.value ? e.value : 0),
                                ),
                            },
                    ],
                };
            },
            options() {
                const darken =
                    this.miscStore.theme === "light"
                        ? cssVariable("--bs-gray-700")
                        : cssVariable("--bs-gray-800");
                const lighten =
                    this.miscStore.theme === "light"
                        ? cssVariable("--bs-gray-200")
                        : cssVariable("--bs-gray-400");

                return defaultConfig(
                    {
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
                    },
                    this.miscStore.theme,
                );
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
            loadQuery(base) {
                return {
                    ...base
                };
            },
            loadMetrics() {
                this.flowStore.loadTasksWithMetrics({
                    ...this.$route.params,
                });
                this.flowStore[this.$route.query.task ? "loadTaskMetrics" : "loadFlowMetrics"](
                    this.loadQuery({
                        ...this.$route.params,
                        taskId: this.$route.query.task,
                    }),
                ).then(() => {
                    if ((this.flowStore.metrics?.length ?? -1) > 0) {
                        if (
                            this.$route.query.metric &&
                            !this.flowStore.metrics?.includes(this.$route.query.metric)
                        ) {
                            let query = {...this.$route.query};
                            delete query.metric;

                            this.$router
                                .push({query: query})
                                .then(() => this.loadAggregatedMetrics());
                        } else {
                            this.loadAggregatedMetrics();
                        }
                    }
                });
            },
            loadAggregatedMetrics() {
                this.isLoading = true;

                if (this.display) {
                    this.flowStore[this.$route.query?.task ? "loadTaskAggregatedMetrics" : "loadFlowAggregatedMetrics"](
                        this.loadQuery({
                            ...this.$route.params,
                            ...this.$route.query,
                            metric: this.$route.query.metric,
                            aggregation: [this.$route.query.aggregation].flat().map(item => item.toLowerCase()),
                            taskId: this.$route.query.task,
                            startDate: this.$route.query.startDate,
                            endDate: this.$route.query.endDate
                        }),
                    );
                } else {
                    this.flowStore.aggregatedMetrics = undefined;
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
    });
</script>

<style scoped>
.navbar-flow-metrics {
    display: flex;
    width: 100%;
}
</style>
