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
    import {ref, computed, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {Bar} from "vue-chartjs";
    import moment from "moment";
    import {useI18n} from "vue-i18n";
    import {useMiscStore} from "override/stores/misc";
    import {useFlowStore} from "../../stores/flow";
    import {defaultConfig, getFormat, tooltip} from "../dashboard/composables/charts";
    import {cssVariable} from "@kestra-io/ui-libs";
    import KSFilter from "../filter/components/KSFilter.vue";
    import {useFlowMetricFilter} from "../filter/configurations";

    defineOptions({
        name: "FlowMetrics",
        inheritAttrs: false,
    });

    const route = useRoute();
    const router = useRouter();
    const {t} = useI18n();

    const flowMetricFilter = useFlowMetricFilter();
    const miscStore = useMiscStore();
    const flowStore = useFlowStore();

    const tooltipContent = ref("");
    const isLoading = ref(false);

    interface MetricAggregation {
        date: string;
        value?: number;
    }

    const display = computed(() => {
        return route.query.metric && route.query.aggregation;
    });

    const chartData = computed(() => {
        const aggregations = (flowStore.aggregatedMetrics?.aggregations ?? []) as MetricAggregation[];
        const groupBy = flowStore.aggregatedMetrics?.groupBy;
        
        const aggregationQuery = route.query.aggregation;
        const aggregationValue = Array.isArray(aggregationQuery) 
            ? aggregationQuery[0] 
            : aggregationQuery;
        const aggregationLabel = aggregationValue?.toLowerCase() ?? "";
        
        return {
            labels: aggregations.map((e: MetricAggregation) =>
                moment(e.date).format(getFormat(groupBy)),
            ),
            datasets: [
                !display.value
                    ? {data: [] as number[], label: "", backgroundColor: ""}
                    : {
                        label: `${t(aggregationLabel)} ${t("of")} ${route.query.metric}`,
                        backgroundColor: cssVariable("--el-color-success"),
                        borderRadius: 4,
                        data: aggregations.map(
                            (e: MetricAggregation) => (e.value ? e.value : 0),
                        ),
                    },
            ],
        };
    });

    const options = computed(() => {
        const darken =
            miscStore.theme === "light"
                ? cssVariable("--bs-gray-700")
                : cssVariable("--bs-gray-800");
        const lighten =
            miscStore.theme === "light"
                ? cssVariable("--bs-gray-200")
                : cssVariable("--bs-gray-400");

        return defaultConfig(
            {
                plugins: {
                    tooltip: {
                        external: (context: { tooltip: any }) => {
                            tooltipContent.value = tooltip(context.tooltip) ?? "";
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
            miscStore.theme,
        );
    });

    function loadMetrics(): void {
        const params = route.params as { namespace: string; id: string };
        
        flowStore.loadTasksWithMetrics({
            namespace: params.namespace,
            id: params.id,
        });
        
        const taskId = route.query.task as string | undefined;
        
        if (taskId) {
            flowStore.loadTaskMetrics({
                namespace: params.namespace,
                id: params.id,
                taskId: taskId,
            }).then(handleMetricsLoaded);
        } else {
            flowStore.loadFlowMetrics({
                namespace: params.namespace,
                id: params.id,
            }).then(handleMetricsLoaded);
        }
    }
    
    function handleMetricsLoaded(): void {
        if ((flowStore.metrics?.length ?? -1) > 0) {
            if (
                route.query.metric &&
                !flowStore.metrics?.includes(route.query.metric as string)
            ) {
                const query = {...route.query};
                delete query.metric;

                router
                    .push({query: query})
                    .then(() => loadAggregatedMetrics());
            } else {
                loadAggregatedMetrics();
            }
        }
    }

    function loadAggregatedMetrics(): void {
        isLoading.value = true;

        if (display.value) {
            const params = route.params as { namespace: string; id: string };
            const metric = route.query.metric as string;
            const taskId = route.query.task as string | undefined;
            
            if (taskId) {
                flowStore.loadTaskAggregatedMetrics({
                    namespace: params.namespace,
                    id: params.id,
                    taskId: taskId,
                    metric: metric,
                });
            } else {
                flowStore.loadFlowAggregatedMetrics({
                    namespace: params.namespace,
                    id: params.id,
                    metric: metric,
                });
            }
        } else {
            flowStore.aggregatedMetrics = undefined;
        }
        isLoading.value = false;
    }

    function load(): void {
        if (!route.query.metric) {
            loadMetrics();
        } else {
            loadAggregatedMetrics();
        }
    }

    // Watch for route query changes
    watch(
        () => route.query,
        (query) => {
            if (!query.metric) {
                loadMetrics();
            } else {
                loadAggregatedMetrics();
            }
        },
    );

    // Initial load (equivalent to created hook)
    loadMetrics();
</script>

<style scoped>
.navbar-flow-metrics {
    display: flex;
    width: 100%;
}
</style>
